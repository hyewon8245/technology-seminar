package com.fintech.loan.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ProductPopularityDTO implements Serializable  {
    
    private Long productId;
    private String productName;
    private Double popularityScore; // 시간 가중 인기도 점수
    private Long totalViews; // 전체 조회수
    private Long recentViews; // 최근 조회수 (7일)
    private LocalDateTime lastViewed; // 마지막 조회 시간
    private Double trendScore; // 트렌드 점수 (최근 vs 과거 비교)
    
    public ProductPopularityDTO(Long productId, String productName, Double popularityScore) {
        this.productId = productId;
        this.productName = productName;
        this.popularityScore = popularityScore;
    }
} 