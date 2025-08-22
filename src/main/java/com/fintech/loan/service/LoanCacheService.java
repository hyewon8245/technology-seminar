package com.fintech.loan.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.loan.domain.LoanDTO;
import com.fintech.loan.domain.entity.Loan;
import com.fintech.loan.repository.LoanRepository;
import com.fintech.loan.repository.LoanViewRepository;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanCacheService {

    private final LoanRepository loanRepository;
    private final LoanViewRepository loanViewRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry; // ✅ Prometheus 지표 수집용

    private static final String LOAN_KEY_PREFIX = "loan:";
    private static final String DATA_KEY  = "loan:loanData";

    @Transactional
    public void incrementViewCount(Long loanId) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        Double currentScore = zSetOps.score(DATA_KEY, loanId);

        if (currentScore == null) {
            Long dbViewCount = loanRepository.findViewCountById(loanId)
                    .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
            zSetOps.add(DATA_KEY, loanId, dbViewCount + 1.0);
        } else {
            zSetOps.incrementScore(DATA_KEY, loanId, 1.0);
        }
    }
 
    public List<LoanDTO> getTop20Loans() {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

        Set<Object> topLoanIds = zSetOps.reverseRange(DATA_KEY, 0, 19);
        if (topLoanIds == null || topLoanIds.isEmpty()) {
            return Collections.emptyList();
        }

        return topLoanIds.stream()
                .map(id -> {
                    Long loanId = Long.valueOf(id.toString());
                    Map<String, Object> loanCache = (Map<String, Object>) valueOps.get(LOAN_KEY_PREFIX + loanId);
                    if (loanCache == null) {
                        Loan loan = loanRepository.findById(loanId).orElse(null);
                        if (loan != null) {
                            Map<String, Object> newCache = Map.of(
                                    "id", loan.getId(),
                                    "productName", loan.getProductName(),
                                    "bank", loan.getBank(),
                                    "jobType", loan.getJobType(),
                                    "purpose", loan.getPurpose(),
                                    "rateType", loan.getRateType(),
                                    "interestRate", loan.getInterestRate(),
                                    "maxLimit", loan.getMaxLimit(),
                                    "periodMonths", loan.getPeriodMonths()
                            );
                            valueOps.set(LOAN_KEY_PREFIX + loanId, newCache);
                            loanCache = newCache;
                        }
                    }

                    if (loanCache != null) {
                        Double score = zSetOps.score(DATA_KEY, loanId);
                        long viewCount = (score != null) ? score.longValue() : 0L;

                        return new LoanDTO(
                                Long.valueOf(loanCache.get("id").toString()),
                                (String) loanCache.get("productName"),
                                (String) loanCache.get("bank"),
                                (String) loanCache.get("jobType"),
                                (String) loanCache.get("purpose"),
                                (String) loanCache.get("rateType"),
                                (String) loanCache.get("interestRate"),
                                (Integer) loanCache.get("maxLimit"),
                                (Integer) loanCache.get("periodMonths"),
                                viewCount
                        );
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void cacheTop20Loans() {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

        Set<Object> topLoanIds = zSetOps.reverseRange(DATA_KEY, 0, 19);
        if (topLoanIds == null || topLoanIds.isEmpty()) {
            log.info("⚠️ ZSet 데이터 없음 - 캐싱 작업 종료");
            return;
        }

        Set<String> existingCacheKeys = redisTemplate.keys("loan:*");
        if (existingCacheKeys != null && !existingCacheKeys.isEmpty()) {
            existingCacheKeys.remove(DATA_KEY);
            redisTemplate.delete(existingCacheKeys);
        }

        for (Object id : topLoanIds) {
            Long loanId = Long.valueOf(id.toString());
            Loan loan = loanRepository.findById(loanId).orElse(null);
            if (loan == null) continue;

            Map<String, Object> loanCache = Map.of(
                    "id", loan.getId(),
                    "productName", loan.getProductName(),
                    "bank", loan.getBank(),
                    "jobType", loan.getJobType(),
                    "purpose", loan.getPurpose(),
                    "rateType", loan.getRateType(),
                    "interestRate", loan.getInterestRate(),
                    "maxLimit", loan.getMaxLimit(),
                    "periodMonths", loan.getPeriodMonths()
            );

//            valueOps.set(LOAN_KEY_PREFIX + loanId, loanCache);
//            log.info("✅ 캐싱 완료 loanId={}", loanId);
             // TTL 설정 (분)
             valueOps.set(LOAN_KEY_PREFIX + loanId, loanCache, java.time.Duration.ofMinutes(2));
             log.info("✅ 캐싱 완료 loanId={} (TTL: 2분)", loanId);
        }
    }

    @Cacheable(value = "loan", key = "#loanId", condition = "false")
    public LoanDTO getLoanCheckCache(Long loanId) {
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

        Map<String, Object> loanCache = (Map<String, Object>) valueOps.get(LOAN_KEY_PREFIX + loanId);

        if (loanCache == null) {
            // ❌ Cache MISS
            meterRegistry.counter("loan_cache_miss").increment(); // ✅ MISS 카운터
            log.info("❌ Cache MISS → DB 조회: loanId={}", loanId);

            Loan loan = loanRepository.findById(loanId)
                    .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

            Double score = zSetOps.score(DATA_KEY, loanId);
            long viewCount = (score != null) ? score.longValue() : 0L;

            return new LoanDTO(
                    loan.getId(),
                    loan.getProductName(),
                    loan.getBank(),
                    loan.getJobType(),
                    loan.getPurpose(),
                    loan.getRateType(),
                    loan.getInterestRate(),
                    loan.getMaxLimit(),
                    loan.getPeriodMonths(),
                    viewCount
            );
        }

        // ✅ Cache HIT
        log.info("✅ Cache HIT: loanId={}", loanId);
        meterRegistry.counter("loan_cache_hit").increment(); // ✅ HIT 카운터
        
        // TTL 재갱신 (분)
        valueOps.set(LOAN_KEY_PREFIX + loanId, loanCache, java.time.Duration.ofMinutes(2));
        log.info("🔄 TTL 재갱신 완료: loanId={} (TTL: 2분)", loanId);

        Double score = zSetOps.score(DATA_KEY, loanId);
        long viewCount = (score != null) ? score.longValue() : 0L;

        return new LoanDTO(
                Long.valueOf(loanCache.get("id").toString()),
                (String) loanCache.get("productName"),
                (String) loanCache.get("bank"),
                (String) loanCache.get("jobType"),
                (String) loanCache.get("purpose"),
                (String) loanCache.get("rateType"),
                (String) loanCache.get("interestRate"),
                (Integer) loanCache.get("maxLimit"),
                (Integer) loanCache.get("periodMonths"),
                viewCount
        );
    }
}
