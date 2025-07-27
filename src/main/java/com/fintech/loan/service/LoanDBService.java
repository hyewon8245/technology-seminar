package com.fintech.loan.service;

import com.fintech.loan.domain.dto.ProductPopularityDTO;
import com.fintech.loan.domain.entity.Loan;
import com.fintech.loan.domain.entity.LoanViewHistory;
import com.fintech.loan.repository.LoanRepository;
import com.fintech.loan.repository.LoanViewHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanDBService {

    private final LoanRepository loanRepository;
    private final LoanViewHistoryRepository viewHistoryRepository;

    // 시간 가중치 계산 상수 (시현용: 10분 단위)
    private static final int RECENT_MINUTES = 10; // 최근 10분
    private static final int POPULARITY_WINDOW_MINUTES = 10; // 인기도 계산 윈도우(10분)
    private static final double RECENT_WEIGHT = 1.0; // 최근 조회 가중치
    private static final double DECAY_RATE = 0.1; // 시간 감쇠율

    /**
     * 제품 조회 시 히스토리 기록
     */
    @Transactional
    public void recordProductView(Long productId, String userIp) {
        LocalDateTime now = LocalDateTime.now();
        double weight = calculateTimeWeight(now);
        
        LoanViewHistory history = new LoanViewHistory(productId, now, weight);
        viewHistoryRepository.save(history);
        
        log.info("Product view recorded: productId={}, weight={}", productId, weight);
    }

    /**
     * 시간 가중치 계산 (최근일수록 높은 가중치)
     */
    private double calculateTimeWeight(LocalDateTime viewTime) {
        LocalDateTime now = LocalDateTime.now();
        long minutesDiff = java.time.Duration.between(viewTime, now).toMinutes();
        
        // 최근 10분 내 조회는 높은 가중치, 그 이후는 지수 감쇠
        if (minutesDiff <= RECENT_MINUTES) {
            return RECENT_WEIGHT;
        } else {
            return RECENT_WEIGHT * Math.exp(-DECAY_RATE * (minutesDiff - RECENT_MINUTES));
        }
    }

    /**
     * 인기도 점수 계산 및 캐시할 제품 목록 반환
     */
    @Cacheable(value = "popularProducts", key = "'topProducts'")
    public List<ProductPopularityDTO> getTopPopularProducts(int limit) {
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(POPULARITY_WINDOW_MINUTES);
        LocalDateTime recentTime = LocalDateTime.now().minusMinutes(RECENT_MINUTES);
        
        // 1. 시간 가중 인기도 점수 계산
        List<Object[]> popularityScores = viewHistoryRepository.findPopularityScores(startTime);
        
        // 2. 최근 조회수 계산
        List<Object[]> recentViews = viewHistoryRepository.findRecentViewCounts(recentTime);
        Map<Long, Long> recentViewMap = recentViews.stream()
                .collect(Collectors.toMap(
                    row -> (Long) row[0], 
                    row -> (Long) row[1]
                ));
        
        // 3. 전체 조회수 계산
        List<Object[]> totalViews = viewHistoryRepository.findTotalViewCounts();
        Map<Long, Long> totalViewMap = totalViews.stream()
                .collect(Collectors.toMap(
                    row -> (Long) row[0], 
                    row -> (Long) row[1]
                ));
        
        // 4. 최근 조회 시간 조회
        List<Object[]> latestViews = viewHistoryRepository.findLatestViewTimeByProduct();
        Map<Long, LocalDateTime> latestViewMap = latestViews.stream()
                .collect(Collectors.toMap(
                    row -> (Long) row[0], 
                    row -> (LocalDateTime) row[1]
                ));
        
        // 5. 제품 정보 조회
        List<Loan> allProducts = loanRepository.findAll();
        Map<Long, String> productNameMap = allProducts.stream()
                .collect(Collectors.toMap(Loan::getId, Loan::getProductName));
        
        // 6. 인기도 DTO 생성
        List<ProductPopularityDTO> popularProducts = popularityScores.stream()
                .limit(limit)
                .map(row -> {
                    Long productId = (Long) row[0];
                    Double popularityScore = (Double) row[1];
                    
                    ProductPopularityDTO dto = new ProductPopularityDTO(
                        productId,
                        productNameMap.getOrDefault(productId, "Unknown"),
                        popularityScore
                    );
                    
                    dto.setRecentViews(recentViewMap.getOrDefault(productId, 0L));
                    dto.setTotalViews(totalViewMap.getOrDefault(productId, 0L));
                    dto.setLastViewed(latestViewMap.get(productId));
                    dto.setTrendScore(calculateTrendScore(productId, recentViewMap, totalViewMap));
                    
                    return dto;
                })
                .collect(Collectors.toList());
        
        log.info("Top {} popular products calculated", popularProducts.size());
        return popularProducts;
    }

    /**
     * 트렌드 점수 계산 (최근 인기도 vs 전체 인기도)
     */
    private double calculateTrendScore(Long productId, Map<Long, Long> recentViews, Map<Long, Long> totalViews) {
        Long recent = recentViews.getOrDefault(productId, 0L);
        Long total = totalViews.getOrDefault(productId, 0L);
        
        if (total == 0) return 0.0;
        
        // 최근 10분 조회 비율이 높을수록 트렌드 점수 높음
        double recentRatio = (double) recent / RECENT_MINUTES;
        double totalRatio = (double) total / POPULARITY_WINDOW_MINUTES;
        
        return recentRatio / totalRatio;
    }

    /**
     * 캐시에서 제거할 제품 목록 반환 (인기도 하락 제품)
     */
    public List<Long> getProductsToRemoveFromCache() {
        List<ProductPopularityDTO> currentPopular = getTopPopularProducts(100); // 충분히 많은 제품 조회
        
        // 트렌드 점수가 낮고 최근 조회가 없는 제품들 필터링 (10분 단위)
        return currentPopular.stream()
                .filter(product -> 
                    product.getTrendScore() < 0.5 && // 트렌드 점수 낮음
                    product.getRecentViews() < 3 && // 최근 10분 조회 3회 미만
                    product.getLastViewed() != null &&
                    product.getLastViewed().isBefore(LocalDateTime.now().minusMinutes(RECENT_MINUTES)) // 10분 이상 조회 없음
                )
                .map(ProductPopularityDTO::getProductId)
                .collect(Collectors.toList());
    }

    /**
     * 제품 상세 정보 조회
     */
    @Cacheable(value = "productDetails", key = "#productId")
    public Optional<Loan> getProductById(Long productId) {
        return loanRepository.findById(productId);
    }

    /**
     * 모든 제품 조회
     */
    public List<Loan> getAllProducts() {
        return loanRepository.findAll();
    }
}
