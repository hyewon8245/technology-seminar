package com.fintech.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.fintech.loan.domain.entity.Loan;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long>{
	
}
