package com.demo.controller;

import com.demo.dto.OrderDto;
import com.demo.service.OrderService;

public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    public OrderDto create(OrderDto dto) {
        return service.create(dto);
    }
}
