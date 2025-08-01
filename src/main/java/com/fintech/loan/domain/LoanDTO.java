package com.fintech.loan.domain;

import java.io.Serializable;

import com.fintech.loan.domain.entity.Loan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class LoanDTO implements Serializable {

	
	public LoanDTO(Long id, String productName, Long viewCount) {
        this.id = id;
        this.productName = productName;
        this.viewCount = viewCount;
    }
	private Long id;
    private String productName;
    private String bank;
    private String jobType;
    private String purpose;
    private String rateType;
    private String interestRate;
    private int maxLimit;
    private int periodMonths;
    private Long viewCount;
    
    public LoanDTO(Loan loan, long viewCount) {
        this.id = loan.getId();
        this.productName = loan.getProductName();
        this.bank = loan.getBank();
        this.jobType = loan.getJobType();
        this.purpose = loan.getPurpose();
        this.rateType = loan.getRateType();
        this.interestRate = loan.getInterestRate();
        this.maxLimit = loan.getMaxLimit();
        this.periodMonths = loan.getPeriodMonths();
        this.viewCount = viewCount;
    }
    
}
