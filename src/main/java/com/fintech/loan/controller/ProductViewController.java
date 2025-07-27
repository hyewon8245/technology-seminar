package com.fintech.loan.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fintech.loan.domain.dto.ProductPopularityDTO;
import com.fintech.loan.service.LoanDBService;

@RestController
@RequestMapping("/api")
public class ProductViewController {

    private final LoanDBService loanDBService;

    public ProductViewController(LoanDBService loanDBService) {
        this.loanDBService = loanDBService;
    }

    @PostMapping("/view")
    public String recordProductView(@RequestParam Long productId) {
        loanDBService.recordProductView(productId, "127.0.0.1");
        return "View recorded for productId: " + productId;
    }
    @GetMapping("/popular-products")
    public List<ProductPopularityDTO> getPopularProducts() {
        return loanDBService.getTopPopularProducts(10);
    }

}
