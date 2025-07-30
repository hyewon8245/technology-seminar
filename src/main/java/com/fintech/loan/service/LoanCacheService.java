package com.fintech.loan.service;

import com.fintech.loan.domain.LoanDTO;
import com.fintech.loan.domain.entity.Loan;
import com.fintech.loan.domain.entity.LoanView;
import com.fintech.loan.repository.LoanRepository;
import com.fintech.loan.repository.LoanViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanCacheService {

    private final LoanRepository loanRepository;
    private final LoanViewRepository loanViewRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOAN_KEY_PREFIX = "loan:";
    private static final String DATA_KEY  = "loan:loanData";

    @Transactional
    public void incrementViewCount(Long loanId) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

        // 1. ZSet에 있는지 확인
        Double currentScore = zSetOps.score(DATA_KEY, loanId);

        if (currentScore == null) {
            // 2. ZSet에 없으면 DB에서 조회수 가져오기
            Long dbViewCount = loanRepository.findViewCountById(loanId)
                    .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

            // 3. DB 조회수 +1 해서 ZSet에 추가
            zSetOps.add(DATA_KEY, loanId, dbViewCount + 1.0);

        } else {
            // 4. ZSet에 있으면 +1
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

                    // Redis에서 Map 가져오기
                    Map<String, Object> loanCache = (Map<String, Object>) valueOps.get(LOAN_KEY_PREFIX + loanId);
                    if (loanCache == null) {
                        // 캐시가 없으면 DB 조회 → 캐싱
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

                        // LoanDTO 생성
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

        // 기존 캐시 삭제 (ZSet 제외)
        Set<String> existingCacheKeys = redisTemplate.keys("loan:*");
        if (existingCacheKeys != null && !existingCacheKeys.isEmpty()) {
            existingCacheKeys.remove(DATA_KEY);
            redisTemplate.delete(existingCacheKeys);
        }

        for (Object id : topLoanIds) {
            Long loanId = Long.valueOf(id.toString());
            Loan loan = loanRepository.findById(loanId).orElse(null);
            if (loan == null) continue;

            // Redis에 필요한 필드만 Map으로 저장
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

            valueOps.set(LOAN_KEY_PREFIX + loanId, loanCache);

            log.info("✅ 캐싱 완료 loanId={}", loanId);
        }
    }
    
    public LoanDTO getLoanCheckCache(Long loanId) {
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();

        // 캐시에서 Map 가져오기
        Map<String, Object> loanCache = (Map<String, Object>) valueOps.get(LOAN_KEY_PREFIX + loanId);

        if (loanCache == null) {
            // ❌ Cache MISS → 캐시 저장하지 않고 바로 DB에서 조회
            log.info("❌ Cache MISS → DB 조회: loanId={}", loanId);

            Loan loan = loanRepository.findById(loanId)
                    .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

            // 최신 viewCount는 ZSet에서 조회
            Double score = zSetOps.score(DATA_KEY, loanId);
            long viewCount = (score != null) ? score.longValue() : 0L;

            // DB 데이터를 그대로 LoanDTO로 변환하여 반환 (캐시 저장 안함)
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

        // ✅ Cache HIT → 캐시 데이터 사용
        log.info("✅ Cache HIT: loanId={}", loanId);

        // 최신 viewCount는 ZSet에서 조회
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
