package com.fintech.loan.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Table(name="LOAN_VIEW_HISTORY")
@Entity
public class LoanViewHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID") 
    private Long id;

    @Column(name = "PRODUCT_ID")
    private Long productId;

    @Column(name = "VIEW_TIME")
    private LocalDateTime viewTime;

    @Column(name = "WEIGHT")
    private Double weight; // 시간 가중치

    @Column(name = "USER_IP")
    private String userIp; // 중복 조회 방지용 (선택사항)

    public LoanViewHistory(Long productId, LocalDateTime viewTime, Double weight) {
        this.productId = productId;
        this.viewTime = viewTime;
        this.weight = weight;
    }
} 