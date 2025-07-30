package com.fintech.loan.controller;

import com.fintech.loan.domain.LoanDTO;
import com.fintech.loan.domain.entity.Loan;
import com.fintech.loan.repository.LoanViewRepository;
import com.fintech.loan.service.LoanDBService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/oracle")
@RequiredArgsConstructor
public class DBController {

    private final LoanDBService loanDBService;
    private final LoanViewRepository loanViewRepository; // ✅ 추가

    /** Oracle DB에서 조회수 증가 */	//ok
    @PostMapping("/view/{loanId}")
    public ResponseEntity<?> increaseView(@PathVariable Long loanId) {
        Loan loan = loanDBService.incrementViewCountInDB(loanId); // Loan 반환하도록 변경
        Long viewCount = loanViewRepository.getViewCount(loanId); // ✅ 조회수 가져오기

        return ResponseEntity.ok(
                new LoanDTO(
                        loan.getId(),
                        loan.getProductName(),
                        viewCount != null ? viewCount : 0L
                )
        );
    }

    /** 인기 상품 Top N 조회 */
    @GetMapping("/popular")
    public List<LoanDTO> getPopularLoans(@RequestParam(defaultValue = "20") int limit) {
        return loanDBService.getPopularLoansFromDB(limit);
    }

    /** 전체 Loan 목록 조회 */	//ok
    @GetMapping("/list")
    public List<LoanDTO> getAllLoans() {
        return loanDBService.getAllLoans();
    }

    /** 단일 Loan 상세 조회 */
    @GetMapping("/detail/{loanId}")	//ok
    public LoanDTO getLoanDetail(@PathVariable Long loanId) {
        return loanDBService.getLoanDetail(loanId);
    }

    /** 전체 조회수 합계 */	//ok
    @GetMapping("/total-views")
    public Long getTotalViewCount() {
        return loanDBService.getTotalViewCount();
    }
}
