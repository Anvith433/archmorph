package com.shop.controller;

import com.shop.dto.OrderDto;
import com.shop.mapper.OrderMapper;
import com.shop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable long id) {
        return orderMapper.toDto(orderService.find(id));
    }

    @GetMapping("/{id}/summary")
    public String summary(@PathVariable long id) {
        return orderService.describe(id);
    }
}
