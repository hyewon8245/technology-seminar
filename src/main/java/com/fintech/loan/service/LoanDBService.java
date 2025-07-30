package com.fintech.loan.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.loan.domain.LoanDTO;
import com.fintech.loan.domain.entity.Loan;
import com.fintech.loan.domain.entity.LoanView;
import com.fintech.loan.repository.LoanRepository;
import com.fintech.loan.repository.LoanViewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoanDBService {

    private final LoanRepository loanRepository;
    private final LoanViewRepository loanViewRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String DATA_KEY  = "loan:loanData";

    /**
     * Oracle DB에서 조회수 증가 (캐싱 없음)
     */
    @Transactional
    public Loan incrementViewCountInDB(Long loanId) {
        // Loan 존재 여부 확인
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
        
        if (loanViewRepository.getViewCount(loanId) == null) {
            loanViewRepository.save(new LoanView(loanId, 0L));
        }
        
        // LoanView 조회수 증가
        loanViewRepository.incrementViewCount(loanId);

        return loan; // ✅ Loan 반환
    }

    /**
     * Oracle DB에서 Loan 상세 조회
     */
    public LoanDTO getLoanDetail(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        Long viewCount = loanViewRepository.getViewCount(loanId);

        return new LoanDTO(
                loan.getId(),
                loan.getProductName(),
                viewCount != null ? viewCount : 0L
        );
    }

    /**
     * 인기 상품 Top N 조회
     */
    public List<LoanDTO> getPopularLoansFromDB(int limit) {
        return loanRepository.findTopLoans(limit).stream()
                .map(l -> new LoanDTO(
                        l.getId(),
                        l.getProductName(),
                        loanViewRepository.getViewCount(l.getId()) != null ? loanViewRepository.getViewCount(l.getId()) : 0L
                ))
                .collect(Collectors.toList());
    }

    /**
     * 전체 상품 목록 조회 (성능 비교용)
     */
    public List<LoanDTO> getAllLoans() {
        return loanRepository.findAll().stream()
                .map(l -> new LoanDTO(
                        l.getId(),
                        l.getProductName(),
                        loanViewRepository.getViewCount(l.getId()) != null ? loanViewRepository.getViewCount(l.getId()) : 0L
                ))
                .collect(Collectors.toList());
    }

    /**
     * DB 전체 조회수 총합 (통계용)
     */
    public Long getTotalViewCount() {
        return loanViewRepository.findAll().stream()
                .mapToLong(v -> v.getViewCount() != null ? v.getViewCount() : 0L)
                .sum();
    }

    /**
     * Redis → Oracle DB 인기상품 조회수 동기화
     */
    @Transactional
    public void syncPopularLoansFromRedis() {
        log.info("🔄 Redis → Oracle DB 동기화 실행");

        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<Object>> allLoans = zSetOps.rangeWithScores(DATA_KEY, 0, -1);

        if (allLoans == null || allLoans.isEmpty()) {
            log.info("⚠️ Redis ZSet 데이터 없음, 동기화 종료");
            return;
        }

        for (ZSetOperations.TypedTuple<Object> tuple : allLoans) {
            // 1. loanId 변환 (toString() → Long.valueOf())
            Object value = tuple.getValue();
            if (value == null) continue;

            Long loanId = Long.valueOf(value.toString());
            Double score = tuple.getScore();
            if (score == null) continue;

            // 2. Loan 존재 여부 확인 후 viewCount 업데이트
            Loan loan = loanRepository.findById(loanId).orElse(null);
            if (loan == null) {
                log.warn("⚠️ DB에 없는 Loan ID 스킵: {}", loanId);
                continue;
            }

            LoanView loanView = loanViewRepository.findById(loanId)
                    .orElse(new LoanView(loanId, 0L));

            loanView.setViewCount(score.longValue());
            loanViewRepository.save(loanView);

            log.info("💾 동기화 완료: loanId={}, viewCount={}", loanId, score.longValue());
        }
    }


}