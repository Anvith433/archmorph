package com.demo.service;

import com.demo.dto.PaymentDto;
import com.demo.entity.Payment;
import com.demo.repository.PaymentRepository;
import org.springframework.stereotype.Service;

/**
 * Charges orders. See {@link com.demo.service.OrderService} and {@link com.demo.common.AppConstants}.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

    public PaymentService(PaymentRepository paymentRepository, OrderService orderService) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
    }

    public PaymentDto pay(PaymentDto dto) {
        Payment payment = new Payment();
        payment.setId(dto.getId());
        payment.setOrder(orderService.require(dto.getOrderId()));
        payment.setAmount(dto.getAmount());
        paymentRepository.save(payment);
        return dto;
    }

    public String currency() {
        return com.demo.common.AppConstants.DEFAULT_CURRENCY;
    }
}
