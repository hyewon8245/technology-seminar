package com.fintech.loan.scheduler;

import java.util.Set;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fintech.loan.service.LoanDBService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisViewSyncScheduler {

    private final RedisTemplate<String, Object> redisTemplate;
    private final LoanDBService loanDBService;

    private static final String DATA_KEY  = "loan:loanData";
    private static final String LOAN_KEY_PREFIX = "loan:";
    private static final double DECAY_FACTOR = 0.85;   // 점수 감소 비율
    private static final double MIN_SCORE = 1;       // 최소 점수 이하 삭제 기준

    /**
     * 30분마다 조회수 감쇠 + 낮은 점수 삭제
     */
    //@Scheduled(cron = "0 */3 * * * *")
    //@Scheduled(cron = "0 */1 * * * *")	//1분에 한번
    public void decayAndEvict() {
        log.info("🔄 ZSet Decay + Low-Weight 삭제 시작");

        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<Object>> allLoans = zSetOps.rangeWithScores(DATA_KEY, 0, -1);

        if (allLoans == null || allLoans.isEmpty()) {
            log.info("⚠️ Redis ZSet 데이터 없음");
            return;
        }

        for (ZSetOperations.TypedTuple<Object> tuple : allLoans) {
            Long loanId = Long.valueOf(tuple.getValue().toString());  // 안전하게 변환
            Double score = tuple.getScore();  // 기존 점수 (before)
            if (score == null) continue;

            double decayedScore = score * DECAY_FACTOR; // 감쇠된 점수 (after)

            if (decayedScore <= MIN_SCORE) {
                log.info("🗑 삭제: loanId={}, before={}, after={}", loanId, score, decayedScore);
                redisTemplate.delete(LOAN_KEY_PREFIX + loanId);
                zSetOps.remove(DATA_KEY, loanId);
            } else {
                // 점수 업데이트
                zSetOps.add(DATA_KEY, loanId, decayedScore);

                // 로그 출력 (before, after 함께)
                log.info("✅ 감쇠 후 업데이트: loanId={}, before={}, after={}", loanId, score, decayedScore);
            }
        }

    }



    /**
     * 3시간마다 Redis → Oracle DB 동기화
     */
    //@Scheduled(cron = "0 0 */3 * * *")
    
    //1분마다 실행
    //@Scheduled(cron = "0 * * * * *")   // 1분마다 실행
    //@Scheduled(cron = "*/10 * * * * *")
    public void syncToDatabase() {
        log.info("💾 Redis → Oracle DB 동기화 시작");
        loanDBService.syncPopularLoansFromRedis();
    }
}

