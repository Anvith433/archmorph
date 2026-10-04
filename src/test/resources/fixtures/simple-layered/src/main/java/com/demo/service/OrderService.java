package com.demo.service;

import com.demo.dto.OrderDto;
import com.demo.entity.Order;
import com.demo.repository.OrderRepository;

public class OrderService {
    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public OrderDto create(OrderDto dto) {
        Order entity = new Order();
        entity.setId(dto.id);
        entity.setName(dto.name);
        repository.save(entity);
        return dto;
    }
}
