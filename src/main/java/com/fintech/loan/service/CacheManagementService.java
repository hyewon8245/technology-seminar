package com.fintech.loan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheManagementService {

    private final CacheManager cacheManager;
    private final LoanDBService loanDBService;

    /**
     * 1분마다 캐시 정리 작업 실행 (시현용)
     */
    @Scheduled(cron = "0 */1 * * * ?") // 1분마다 실행
    public void cleanExpiredCache() {
        log.info("Starting cache cleanup job...");
        
        try {
            // 1. 인기도 하락 제품들을 캐시에서 제거
            List<Long> productsToRemove = loanDBService.getProductsToRemoveFromCache();
            
            if (!productsToRemove.isEmpty()) {
                log.info("Removing {} products from cache due to popularity decline", productsToRemove.size());
                
                // 제품 상세 정보 캐시에서 제거
                productsToRemove.forEach(productId -> {
                    cacheManager.getCache("productDetails").evict(productId);
                    log.debug("Removed product {} from cache", productId);
                });
            }
            
            // 2. 인기도 캐시 갱신 (새로운 인기도 순위로 업데이트)
            cacheManager.getCache("popularProducts").clear();
            loanDBService.getTopPopularProducts(50); // 새로운 인기도 순위 계산
            
            // 3. Redis ZSET 오래된 데이터 정리
            loanDBService.cleanupOldRedisData();
            
            log.info("Cache cleanup completed successfully");
            
        } catch (Exception e) {
            log.error("Error during cache cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * 수동 캐시 정리 (API로 호출 가능)
     */
    public void manualCacheCleanup() {
        log.info("Manual cache cleanup requested");
        cleanExpiredCache();
    }

    /**
     * 특정 제품을 캐시에서 제거
     */
    public void removeProductFromCache(Long productId) {
        cacheManager.getCache("productDetails").evict(productId);
        loanDBService.removeProductViewData(productId); // Redis ZSET에서도 제거
        log.info("Product {} manually removed from cache and Redis", productId);
    }

    /**
     * 모든 캐시 초기화
     */
    public void clearAllCaches() {
        cacheManager.getCacheNames().forEach(cacheName -> {
            cacheManager.getCache(cacheName).clear();
            log.info("Cleared cache: {}", cacheName);
        });
    }

    /**
     * Redis ZSET 통계 정보 조회
     */
    public Object getRedisStats() {
        return loanDBService.getRedisStats();
    }
} 