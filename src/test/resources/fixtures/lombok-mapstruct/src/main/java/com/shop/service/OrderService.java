package com.shop.service;

import com.shop.model.Order;
import com.shop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orders;

    public Order find(long id) {
        return orders.find(id);
    }

    /** Uses the customer only through Lombok-generated getters: no import of Customer. */
    public String describe(long id) {
        Order order = orders.find(id);
        return order.getCustomer().getName() + " ordered " + order.getTotal();
    }
}
