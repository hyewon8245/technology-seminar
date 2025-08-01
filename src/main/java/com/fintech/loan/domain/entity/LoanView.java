package com.fintech.loan.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "loan_views")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanView {

    @Id
    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "view_count")
    private Long viewCount;
}
