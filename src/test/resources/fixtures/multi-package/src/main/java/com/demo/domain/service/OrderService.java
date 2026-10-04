package com.demo.domain.service;

import com.demo.domain.repository.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final UserService users;

    public OrderService(OrderRepository repository, UserService users) {
        this.repository = repository;
        this.users = users;
    }

    public com.demo.domain.model.User owner(Long orderId) {
        com.demo.domain.model.Order order = repository.find(orderId);
        return users.get(order.getUserId());
    }
}
