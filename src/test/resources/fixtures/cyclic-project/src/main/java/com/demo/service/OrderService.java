package com.demo.service;

import com.demo.entity.Order;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private PaymentService paymentService;

    public void setPaymentService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public Order place(Order order) {
        paymentService.charge(order);
        return order;
    }
}
