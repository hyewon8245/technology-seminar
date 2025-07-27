package com.fintech.loan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString

public class LoanDTO {

	private Long id;
	private String productName;
	private String bank;
	private String jobType;
	private String purpose;
	private String rateType;
	private String interestRate;
	private int maxLimit;
	private int periodMonths;
}
