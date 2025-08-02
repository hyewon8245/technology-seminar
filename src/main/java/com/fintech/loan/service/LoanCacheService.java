package com.fintech.loan.service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
//
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisCallback;
//
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
        
        meterRegistry.counter("loan_view_increment_total").increment();
    }
    @Transactional
    public void incrementViewCount2(Long loanId) {
        ListOperations<String, Object> listOps = redisTemplate.opsForList();
        final String LRU_KEY = "loan:lru";
        final int MAX_LRU_SIZE = 20;
        meterRegistry.counter("loan_view_increment_total").increment();
        listOps.getOperations().execute((RedisCallback<Void>) connection -> {
            // 직렬화 도구로 key, value 바이트 변환
            var serializer = redisTemplate.getStringSerializer();
            byte[] lruKey = serializer.serialize(LRU_KEY);
            byte[] loanIdBytes = serializer.serialize(loanId.toString());

         // 1. 기존 위치 제거
            connection.lRem(lruKey, 0, loanIdBytes);

            // 2. 가장 앞에 삽입
            connection.lPush(lruKey, loanIdBytes);

            // 3. 초과 항목 확인
            Long currentSize = connection.lLen(lruKey);
            if (currentSize != null && currentSize > MAX_LRU_SIZE) {
                List<byte[]> evictedBytes = connection.lRange(lruKey, MAX_LRU_SIZE, -1);
                if (evictedBytes != null) {
                    for (byte[] b : evictedBytes) {
                        String evictedId = serializer.deserialize(b);
                        String redisKey = LOAN_KEY_PREFIX + evictedId;
                        redisTemplate.delete(redisKey);
                        log.info("🗑️ Evicted from cache: {}", redisKey);
                    }
                }
            }

            // 4. 리스트 길이 제한
            connection.lTrim(lruKey, 0, MAX_LRU_SIZE - 1);

            return null;
        });
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

            valueOps.set(LOAN_KEY_PREFIX + loanId, loanCache);
            log.info("✅ 캐싱 완료 loanId={}", loanId);
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
    @Cacheable(value = "loan", key = "#loanId", condition = "false")
    public LoanDTO getLoanCheckCache2(Long loanId) {
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

        final String LRU_KEY = "loan:lru";
        final int MAX_LRU_SIZE = 20;

        // 1. 캐시에서 데이터 가져오기
        Map<String, Object> loanCache = (Map<String, Object>) valueOps.get(LOAN_KEY_PREFIX + loanId);

        if (loanCache == null) {
            // ❌ Cache MISS
            meterRegistry.counter("loan_cache_miss").increment();
            log.info("❌ Cache MISS → DB 조회: loanId={}", loanId);

            Loan loan = loanRepository.findById(loanId)
                    .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

            // 캐시에 저장
            Map<String, Object> newCache = new HashMap<>();
            newCache.put("id", loan.getId());
            newCache.put("productName", loan.getProductName());
            newCache.put("bank", loan.getBank());
            newCache.put("jobType", loan.getJobType());
            newCache.put("purpose", loan.getPurpose());
            newCache.put("rateType", loan.getRateType());
            newCache.put("interestRate", loan.getInterestRate());
            newCache.put("maxLimit", loan.getMaxLimit());
            newCache.put("periodMonths", loan.getPeriodMonths());

            valueOps.set(LOAN_KEY_PREFIX + loanId, newCache); // TTL은 자유 조정

            // LRU 리스트 갱신
         // LRU 리스트 갱신
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                byte[] keyBytes = redisTemplate.getStringSerializer().serialize(LRU_KEY);
                byte[] valueBytes = redisTemplate.getStringSerializer().serialize(loanId.toString());

                // 1. 기존 중복 제거 + 삽입
                connection.lRem(keyBytes, 0, valueBytes);
                connection.lPush(keyBytes, valueBytes);

                // 2. 초과된 ID 제거
                Long size = connection.lLen(keyBytes);
                if (size != null && size > MAX_LRU_SIZE) {
                    List<byte[]> evicted = connection.lRange(keyBytes, MAX_LRU_SIZE, -1);
                    if (evicted != null) {
                        for (byte[] evictedBytes : evicted) {
                            String evictedId = redisTemplate.getStringSerializer().deserialize(evictedBytes);
                            String redisKey = LOAN_KEY_PREFIX + evictedId;
                            redisTemplate.delete(redisKey);
                            log.info("🗑️ Evicted (getLoanCheckCache2): {}", redisKey);
                        }
                    }
                }

                // 3. 리스트 자르기
                connection.lTrim(keyBytes, 0, MAX_LRU_SIZE - 1);

                return null;
            });


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
                    0L // 조회수 없음 or 직접 조회할 경우 따로 로직 작성
            );
        }

        // ✅ Cache HIT
        log.info("✅ Cache HIT: loanId={}", loanId);
        meterRegistry.counter("loan_cache_hit").increment();

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
                0L // 캐시에는 조회수가 없기 때문에 임시로 0L
        );
    }

}
