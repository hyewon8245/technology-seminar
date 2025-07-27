package com.fintech.loan.controller;

import com.fintech.loan.domain.entity.Loan;
import com.fintech.loan.service.LoanDBService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/db")
@RequiredArgsConstructor
public class DBController {

    private final LoanDBService loanDBService;

    /**
     * 모든 제품 조회
     */
    @GetMapping("/products")
    public ResponseEntity<List<Loan>> getAllProducts() {
        log.info("Requesting all products from DB");
        List<Loan> products = loanDBService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    /**
     * 특정 제품 조회 (Redis ZSET에 조회 기록 추가)
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<Map<String, Object>> getProductById(
            @PathVariable Long productId,
            HttpServletRequest request) {
        
        log.info("Requesting product details: productId={}", productId);
        
        // 클라이언트 IP 주소 가져오기
        String userIp = getClientIpAddress(request);
        
        // Redis ZSET에 조회 기록 추가
        loanDBService.recordProductView(productId, userIp);
        
        // 제품 정보 조회
        Optional<Loan> product = loanDBService.getProductById(productId);
        
        if (product.isPresent()) {
            Map<String, Object> response = Map.of(
                "productId", productId,
                "productName", product.get().getProductName(),
                "bank", product.get().getBank(),
                "interestRate", product.get().getInterestRate(),
                "maxLimit", product.get().getMaxLimit(),
                "periodMonths", product.get().getPeriodMonths(),
                "viewRecorded", true,
                "userIp", userIp
            );
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 인기 제품 목록 조회 (Redis ZSET 기반)
     */
    @GetMapping("/popular")
    public ResponseEntity<List<Map<String, Object>>> getPopularProducts(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Requesting popular products: limit={}", limit);
        List<Map<String, Object>> popularProducts = loanDBService.getTopPopularProducts(limit);
        return ResponseEntity.ok(popularProducts);
    }

    /**
     * Redis ZSET 통계 정보 조회
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        log.info("Requesting Redis statistics");
        Map<String, Object> stats = loanDBService.getRedisStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 클라이언트 IP 주소 가져오기
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0];
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
