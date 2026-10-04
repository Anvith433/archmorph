package com.shop.controller;

import com.shop.dto.OrderDto;
import com.shop.service.OrderService;

public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    public OrderDto create(OrderDto dto) {
        return service.create(dto);
    }
}
