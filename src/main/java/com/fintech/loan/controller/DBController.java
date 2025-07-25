package com.fintech.loan.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fintech.loan.domain.entity.Loan;

@RestController
public class DBController {
	
	@GetMapping("/oracledb")
	public Loan getOracleData() {
		return null;
	}
}
