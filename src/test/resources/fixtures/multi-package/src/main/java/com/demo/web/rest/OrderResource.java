package com.demo.web.rest;

import com.demo.domain.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderResource {

    private final OrderService orders;

    public OrderResource(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public Object get() {
        return orders.owner(1L);
    }
}
