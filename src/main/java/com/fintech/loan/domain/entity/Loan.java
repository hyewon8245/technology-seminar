package com.fintech.loan.domain.entity;

import java.io.Serializable;

import jakarta.persistence.*;
import lombok.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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