package com.fintech.loan.controller;

import com.fintech.loan.service.CacheManagementService;
import com.fintech.loan.service.LoanDBService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/cache")
@RequiredArgsConstructor
public class CacheController {

    private final CacheManagementService cacheManagementService;
    private final LoanDBService loanDBService;

    /**
     * 인기 제품 목록 조회 (Redis ZSET 기반)
     */
    @GetMapping("/popular-products")
    public ResponseEntity<List<Map<String, Object>>> getPopularProducts(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Requesting top {} popular products", limit);
        List<Map<String, Object>> products = loanDBService.getTopPopularProducts(limit);
        return ResponseEntity.ok(products);
    }

    /**
     * 제품 조회 기록 (Redis ZSET에 추가)
     */
    @PostMapping("/record-view/{productId}")
    public ResponseEntity<String> recordProductView(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "127.0.0.1") String userIp) {
        log.info("Recording product view: productId={}, userIp={}", productId, userIp);
        loanDBService.recordProductView(productId, userIp);
        return ResponseEntity.ok("Product view recorded successfully");
    }

    /**
     * 캐시 강제 갱신
     */
    @PostMapping("/refresh")
    public ResponseEntity<String> refreshCache() {
        log.info("Manual cache refresh requested");
        cacheManagementService.manualCacheCleanup();
        return ResponseEntity.ok("Cache refreshed successfully");
    }

    /**
     * 특정 제품 캐시 삭제
     */
    @DeleteMapping("/product/{productId}")
    public ResponseEntity<String> removeProductCache(@PathVariable Long productId) {
        log.info("Removing product cache: productId={}", productId);
        cacheManagementService.removeProductFromCache(productId);
        return ResponseEntity.ok("Product cache removed successfully");
    }

    /**
     * 모든 캐시 초기화
     */
    @DeleteMapping("/all")
    public ResponseEntity<String> clearAllCaches() {
        log.info("Clearing all caches");
        cacheManagementService.clearAllCaches();
        return ResponseEntity.ok("All caches cleared successfully");
    }

    /**
     * Redis ZSET 통계 정보 조회
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        log.info("Requesting cache statistics");
        Map<String, Object> stats = loanDBService.getRedisStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 캐시 성능 테스트 (캐시 hit/miss 확인)
     */
    @GetMapping("/test/{productId}")
    public ResponseEntity<Map<String, Object>> testCachePerformance(@PathVariable Long productId) {
        log.info("Testing cache performance for productId={}", productId);
        
        long startTime = System.currentTimeMillis();
        var product = loanDBService.getProductById(productId);
        long endTime = System.currentTimeMillis();
        
        Map<String, Object> result = Map.of(
            "productId", productId,
            "responseTime", endTime - startTime + "ms",
            "found", product.isPresent(),
            "productName", product.map(p -> p.getProductName()).orElse("Not found")
        );
        
        return ResponseEntity.ok(result);
    }

    /**
     * 인기 제품 순위 변화 시뮬레이션 (시현용)
     */
    @PostMapping("/simulate-trend/{productId}")
    public ResponseEntity<String> simulateTrendChange(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "5") int viewCount) {
        log.info("Simulating trend change: productId={}, viewCount={}", productId, viewCount);
        
        for (int i = 0; i < viewCount; i++) {
            loanDBService.recordProductView(productId, "simulation-" + i);
        }
        
        return ResponseEntity.ok("Trend simulation completed. Product " + productId + " viewed " + viewCount + " times");
    }
}
