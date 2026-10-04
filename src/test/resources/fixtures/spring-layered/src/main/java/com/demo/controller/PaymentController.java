package com.demo.controller;

import com.demo.common.ApiResponse;
import com.demo.dto.PaymentDto;
import com.demo.service.PaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ApiResponse<PaymentDto> pay(@RequestBody PaymentDto dto) {
        return ApiResponse.ok(paymentService.pay(dto));
    }
}
