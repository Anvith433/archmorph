package com.demo.controller;

import com.demo.common.ApiResponse;
import com.demo.entity.Order;
import com.demo.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Object> list() {
        return new ApiResponse<>(service != null ? new Order() : null);
    }
}
