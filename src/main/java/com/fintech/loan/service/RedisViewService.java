package com.fintech.loan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisViewService {

    private final RedisTemplate<String, Object> redisTemplate;

    // ZSET 키 상수
    private static final String TOTAL_VIEWS_KEY = "product:views:total";
    private static final String RECENT_VIEWS_KEY = "product:views:recent:10min";
    private static final String HOURLY_VIEWS_KEY = "product:views:hourly";
    private static final String DAILY_VIEWS_KEY = "product:views:daily";
    
    // 시현용: 10분 단위
    private static final int RECENT_WINDOW_MINUTES = 10;
    private static final int HOURLY_WINDOW_HOURS = 1;
    private static final int DAILY_WINDOW_DAYS = 1;

    /**
     * 제품 조회 기록 (ZSET에 추가)
     */
    public void recordProductView(Long productId, String userIp) {
        LocalDateTime now = LocalDateTime.now();
        
        // 1. 전체 조회수 증가
        redisTemplate.opsForZSet().incrementScore(TOTAL_VIEWS_KEY, productId, 1.0);
        
        // 2. 최근 조회수 증가 (10분 윈도우)
        redisTemplate.opsForZSet().incrementScore(RECENT_VIEWS_KEY, productId, 1.0);
        
        // 3. 시간별 조회수 증가 (1시간 윈도우)
        String hourlyKey = HOURLY_VIEWS_KEY + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        redisTemplate.opsForZSet().incrementScore(hourlyKey, productId, 1.0);
        
        // 4. 일별 조회수 증가 (1일 윈도우)
        String dailyKey = DAILY_VIEWS_KEY + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        redisTemplate.opsForZSet().incrementScore(dailyKey, productId, 1.0);
        
        // 5. 조회 시간 기록 (중복 방지용)
        String viewTimeKey = "product:view:time:" + productId + ":" + userIp;
        redisTemplate.opsForValue().set(viewTimeKey, now.toString(), 
            java.time.Duration.ofMinutes(RECENT_WINDOW_MINUTES));
        
        log.info("Product view recorded in Redis ZSET: productId={}", productId);
    }

    /**
     * 인기 제품 목록 조회 (ZSET에서 상위 N개)
     */
    public List<Map<String, Object>> getTopPopularProducts(int limit) {
        // 최근 10분 조회수 기준으로 상위 제품 조회
        Set<ZSetOperations.TypedTuple<Object>> topProducts = 
            redisTemplate.opsForZSet().reverseRangeWithScores(RECENT_VIEWS_KEY, 0, limit - 1);
        
        if (topProducts == null || topProducts.isEmpty()) {
            return new ArrayList<>();
        }
        
        return topProducts.stream()
            .map(tuple -> {
                Map<String, Object> product = new HashMap<>();
                product.put("productId", Long.valueOf(tuple.getValue().toString()));
                product.put("recentViews", tuple.getScore().longValue());
                product.put("totalViews", getTotalViews(Long.valueOf(tuple.getValue().toString())));
                product.put("trendScore", calculateTrendScore(Long.valueOf(tuple.getValue().toString())));
                return product;
            })
            .collect(Collectors.toList());
    }

    /**
     * 전체 조회수 조회
     */
    public Long getTotalViews(Long productId) {
        Double score = redisTemplate.opsForZSet().score(TOTAL_VIEWS_KEY, productId);
        return score != null ? score.longValue() : 0L;
    }

    /**
     * 최근 조회수 조회
     */
    public Long getRecentViews(Long productId) {
        Double score = redisTemplate.opsForZSet().score(RECENT_VIEWS_KEY, productId);
        return score != null ? score.longValue() : 0L;
    }

    /**
     * 트렌드 점수 계산 (최근 vs 전체)
     */
    public Double calculateTrendScore(Long productId) {
        Long recentViews = getRecentViews(productId);
        Long totalViews = getTotalViews(productId);
        
        if (totalViews == 0) return 0.0;
        
        // 최근 10분 조회 비율이 높을수록 트렌드 점수 높음
        double recentRatio = (double) recentViews / RECENT_WINDOW_MINUTES;
        double totalRatio = (double) totalViews / (RECENT_WINDOW_MINUTES * 6); // 1시간 기준
        
        return recentRatio / totalRatio;
    }

    /**
     * 인기도 하락 제품 목록 (캐시에서 제거할 제품들)
     */
    public List<Long> getProductsToRemoveFromCache() {
        Set<ZSetOperations.TypedTuple<Object>> allProducts = 
            redisTemplate.opsForZSet().rangeWithScores(RECENT_VIEWS_KEY, 0, -1);
        
        if (allProducts == null) return new ArrayList<>();
        
        return allProducts.stream()
            .filter(tuple -> {
                Long productId = Long.valueOf(tuple.getValue().toString());
                Double score = tuple.getScore();
                
                // 최근 10분 조회가 3회 미만이고, 트렌드 점수가 낮은 제품
                return score < 3.0 && calculateTrendScore(productId) < 0.5;
            })
            .map(tuple -> Long.valueOf(tuple.getValue().toString()))
            .collect(Collectors.toList());
    }

    /**
     * 오래된 데이터 정리 (주기적으로 실행)
     */
    public void cleanupOldData() {
        LocalDateTime now = LocalDateTime.now();
        
        // 1시간 전 데이터 정리
        String oldHourlyKey = HOURLY_VIEWS_KEY + ":" + 
            now.minusHours(HOURLY_WINDOW_HOURS + 1).format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        redisTemplate.delete(oldHourlyKey);
        
        // 1일 전 데이터 정리
        String oldDailyKey = DAILY_VIEWS_KEY + ":" + 
            now.minusDays(DAILY_WINDOW_DAYS + 1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        redisTemplate.delete(oldDailyKey);
        
        log.info("Old Redis data cleaned up");
    }

    /**
     * 특정 제품의 모든 조회 데이터 삭제
     */
    public void removeProductData(Long productId) {
        redisTemplate.opsForZSet().remove(TOTAL_VIEWS_KEY, productId);
        redisTemplate.opsForZSet().remove(RECENT_VIEWS_KEY, productId);
        
        // 시간별, 일별 데이터도 삭제 (패턴 매칭으로 찾아서 삭제)
        Set<String> hourlyKeys = redisTemplate.keys(HOURLY_VIEWS_KEY + ":*");
        if (hourlyKeys != null) {
            hourlyKeys.forEach(key -> redisTemplate.opsForZSet().remove(key, productId));
        }
        
        Set<String> dailyKeys = redisTemplate.keys(DAILY_VIEWS_KEY + ":*");
        if (dailyKeys != null) {
            dailyKeys.forEach(key -> redisTemplate.opsForZSet().remove(key, productId));
        }
        
        log.info("Product data removed from Redis: productId={}", productId);
    }

    /**
     * Redis ZSET 통계 정보 조회
     */
    public Map<String, Object> getRedisStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalProducts", redisTemplate.opsForZSet().zCard(TOTAL_VIEWS_KEY));
        stats.put("recentProducts", redisTemplate.opsForZSet().zCard(RECENT_VIEWS_KEY));
        stats.put("topProduct", getTopPopularProducts(1));
        
        return stats;
    }
} 