package com.fintech.loan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fintech.loan.domain.entity.Loan;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

	/** Loan과 LoanView를 조인해서 인기 상품 Top N 조회 */
	@Query(value = "SELECT * FROM ( " + "SELECT l.*, NVL(lv.view_count, 0) AS view_count " + "FROM loan_products l "
			+ "LEFT JOIN loan_views lv ON l.id = lv.loan_id " + "ORDER BY NVL(lv.view_count, 0) DESC "
			+ ") WHERE ROWNUM <= :limit", nativeQuery = true)
	List<Loan> findTopLoans(@Param("limit") int limit);

	@Query(value = "SELECT NVL(lv.view_count, 0) " + "FROM loan_products l "
			+ "LEFT JOIN loan_views lv ON l.id = lv.loan_id " + "WHERE l.id = :loanId", nativeQuery = true)
	Optional<Long> findViewCountById(@Param("loanId") Long loanId);

}