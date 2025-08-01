package com.fintech.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fintech.loan.domain.entity.LoanView;

@Repository
public interface LoanViewRepository extends JpaRepository<LoanView, Long> {

    // 단순히 조회수 가져오기 (Oracle DB 조회용)
    @Query("SELECT v.viewCount FROM LoanView v WHERE v.loanId = :loanId")
    Long getViewCount(@Param("loanId") Long loanId);
    
    @Modifying
    @Query("UPDATE LoanView v SET v.viewCount = v.viewCount + 1 WHERE v.loanId = :loanId")
    void incrementViewCount(@Param("loanId") Long loanId);
}
