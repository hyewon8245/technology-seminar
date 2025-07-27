package com.fintech.loan.controller;
///캐시로 해서 rest로 body넘기면 json return 객체
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fintech.loan.domain.entity.Loan;

@RestController
public class CacheController {
	
	@GetMapping("/redisdb")
	public Loan getRedisData() {
		return null;
	}

	@GetMapping("/redisdb")
	public Loan getRedisDB() {
		return null;
    }

}
