package com.fintech.loan.service;

import com.fintech.loan.domain.entity.Loan;
import com.fintech.loan.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanDBService {

    private final LoanRepository loanRepository;
    private final RedisViewService redisViewService;

    /**
     * 제품 조회 시 Redis ZSET에 기록
     */
    public void recordProductView(Long productId, String userIp) {
        redisViewService.recordProductView(productId, userIp);
    }

    /**
     * 인기 제품 목록 조회 (Redis ZSET에서)
     */
    @Cacheable(value = "popularProducts", key = "'topProducts'")
    public List<Map<String, Object>> getTopPopularProducts(int limit) {
        List<Map<String, Object>> popularProducts = redisViewService.getTopPopularProducts(limit);
        
        // 제품 상세 정보 추가
        return popularProducts.stream()
            .map(product -> {
                Long productId = (Long) product.get("productId");
                Optional<Loan> loan = getProductById(productId);
                if (loan.isPresent()) {
                    product.put("productName", loan.get().getProductName());
                    product.put("bank", loan.get().getBank());
                    product.put("interestRate", loan.get().getInterestRate());
                }
                return product;
            })
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

    /**
     * 캐시에서 제거할 제품 목록 반환 (Redis ZSET 기반)
     */
    public List<Long> getProductsToRemoveFromCache() {
        return redisViewService.getProductsToRemoveFromCache();
    }

    /**
     * Redis ZSET 통계 정보 조회
     */
    public Map<String, Object> getRedisStats() {
        return redisViewService.getRedisStats();
    }

    /**
     * 특정 제품의 조회 데이터 삭제
     */
    public void removeProductViewData(Long productId) {
        redisViewService.removeProductData(productId);
    }

    /**
     * 오래된 Redis 데이터 정리
     */
    public void cleanupOldRedisData() {
        redisViewService.cleanupOldData();
    }
}
