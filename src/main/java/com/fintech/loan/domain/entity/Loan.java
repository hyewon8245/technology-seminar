package com.fintech.loan.domain.entity;

import java.io.Serializable;

import jakarta.persistence.*;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Entity
@Table(name = "LOAN_PRODUCTS")
public class Loan implements Serializable{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID") 
    private Long id;

    @Column(name = "PRODUCT_NAME")
    private String productName;

    @Column(name = "BANK")
    private String bank;

    @Column(name = "JOB_TYPE")
    private String jobType;

    @Column(name = "PURPOSE")
    private String purpose;

    @Column(name = "RATE_TYPE")
    private String rateType;

    @Column(name = "INTEREST_RATE")
    private String interestRate;

    @Column(name = "MAX_LIMIT")
    private int maxLimit;

    @Column(name = "PERIOD_MONTHS")
    private int periodMonths;
}