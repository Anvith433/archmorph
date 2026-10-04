package com.demo.service;

import com.demo.entity.Order;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private OrderService orderService;

    public void setOrderService(OrderService orderService) {
        this.orderService = orderService;
    }

    public void charge(Order order) {
        if (orderService == null) {
            throw new IllegalStateException("wired late");
        }
    }
}
