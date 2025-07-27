package com.fintech.loan.repository;

import com.fintech.loan.domain.entity.LoanViewHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoanViewHistoryRepository extends JpaRepository<LoanViewHistory, Long> {
    
    // 특정 제품의 조회 히스토리 조회
    List<LoanViewHistory> findByProductIdOrderByViewTimeDesc(Long productId);
    
    // 특정 기간 내 조회 히스토리 조회
    @Query("SELECT h FROM LoanViewHistory h WHERE h.viewTime >= :startTime")
    List<LoanViewHistory> findByViewTimeAfter(@Param("startTime") LocalDateTime startTime);
    
    // 제품별 최근 조회 시간 조회
    @Query("SELECT h.productId, MAX(h.viewTime) FROM LoanViewHistory h GROUP BY h.productId")
    List<Object[]> findLatestViewTimeByProduct();
    
    // 제품별 시간 가중 조회수 계산 (최근 30일)
    @Query("SELECT h.productId, SUM(h.weight) FROM LoanViewHistory h " +
           "WHERE h.viewTime >= :startTime GROUP BY h.productId ORDER BY SUM(h.weight) DESC")
    List<Object[]> findPopularityScores(@Param("startTime") LocalDateTime startTime);
    
    // 제품별 최근 7일 조회수
    @Query("SELECT h.productId, COUNT(h) FROM LoanViewHistory h " +
           "WHERE h.viewTime >= :recentTime GROUP BY h.productId")
    List<Object[]> findRecentViewCounts(@Param("recentTime") LocalDateTime recentTime);
    
    // 제품별 전체 조회수
    @Query("SELECT h.productId, COUNT(h) FROM LoanViewHistory h GROUP BY h.productId")
    List<Object[]> findTotalViewCounts();
} 