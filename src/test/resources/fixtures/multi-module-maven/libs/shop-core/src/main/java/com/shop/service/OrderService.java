package com.shop.service;

import com.shop.dto.OrderDto;
import com.shop.entity.Order;
import com.shop.repository.OrderRepository;

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

    public int count() {
        return repository.findAll().size();
    }
}
