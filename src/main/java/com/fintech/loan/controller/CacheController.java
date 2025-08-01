package com.fintech.loan.controller;

import com.fintech.loan.domain.LoanDTO;
import com.fintech.loan.domain.entity.Loan;
import com.fintech.loan.service.LoanCacheService;
import com.fintech.loan.repository.LoanViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/redis")
@RequiredArgsConstructor
public class CacheController {

    private final LoanCacheService loanCacheService;
    private final LoanViewRepository loanViewRepository;
    private final RedisTemplate<String, Object> redisTemplate; // ✅ RedisTemplate 추가

    /** 조회수 증가 (Redis + Oracle Write-Through) */
    @PostMapping("/view/{loanId}")
    public ResponseEntity<?> incrementViewCount(@PathVariable Long loanId) {
        // 조회수 증가 (ZSet만 업데이트)
        loanCacheService.incrementViewCount(loanId);
        
        // Redis ZSet에서 실시간 조회수 가져오기
        Double redisScore = redisTemplate.opsForZSet().score("loan:loanData", loanId);
        Long viewCount = (redisScore != null) ? redisScore.longValue() : 0L;

        // 조회수만 반환 (loan 정보 X)
        return ResponseEntity.ok(Map.of("loanId", loanId, "viewCount", viewCount));
    }
    
    @GetMapping("/popular")
    public void testTop20() {
        loanCacheService.cacheTop20Loans();
    }
    
//    @GetMapping("/popular/{loanId}")
//    public LoanDTO testCacheHitOrMiss(@PathVariable Long loanId) {
//        return loanCacheService.getLoanCheckCache(loanId);
//    }

    /** 단일 Loan 상세 조회 (Cache-Aside) */
    @GetMapping("/detail/{loanId}")	//ok
    public LoanDTO getLoanDetail(@PathVariable Long loanId) {
    	loanCacheService.incrementViewCount(loanId);
    	return loanCacheService.getLoanCheckCache(loanId);
    }

}
