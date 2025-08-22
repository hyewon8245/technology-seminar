package com.fintech.loan.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fintech.loan.service.LoanCacheService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanScheduler {

    private final LoanCacheService loanCacheService;

    // 오전 6시 ~ 오후 11시 59분: 10분마다 인기순위 갱신
    @Scheduled(cron = "0 */3 * * * *") //1분에 한번
    public void updatePopularLoansDaytime() {
        log.info("🌞 [주간] 인기 상품 갱신");
        //loanCacheService.updatePopularLoans();
        loanCacheService.cacheTop20Loans();
    }

    // 자정~5시59분 : 30분마다 인기순위 갱신
    //@Scheduled(cron = "0 */30 0-5 * * *")
    public void updatePopularLoansNight() {
        log.info("🌙 [야간] 인기 상품 갱신");
        loanCacheService.cacheTop20Loans();
    }
    

}

