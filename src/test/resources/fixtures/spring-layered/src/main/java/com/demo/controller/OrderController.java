package com.demo.controller;

import com.demo.common.ApiResponse;
import com.demo.dto.OrderDto;
import com.demo.service.OrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<OrderDto> create(@RequestBody OrderDto dto) {
        return ApiResponse.ok(orderService.create(dto));
    }
}
