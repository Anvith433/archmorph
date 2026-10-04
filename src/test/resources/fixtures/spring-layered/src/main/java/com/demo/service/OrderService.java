package com.demo.service;

import static com.demo.common.AppConstants.DEFAULT_CURRENCY;

import com.demo.entity.Order;
import com.demo.repository.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserService userService;

    public OrderService(OrderRepository orderRepository, UserService userService) {
        this.orderRepository = orderRepository;
        this.userService = userService;
    }

    public com.demo.dto.OrderDto create(com.demo.dto.OrderDto dto) {
        Order order = new Order();
        order.setId(dto.getId());
        order.setUser(userService.require(dto.getUserId()));
        order.setTotal(dto.getTotal());
        orderRepository.save(order);
        return dto;
    }

    public Order require(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Order " + id));
    }

    public String currency() {
        return DEFAULT_CURRENCY;
    }
}
