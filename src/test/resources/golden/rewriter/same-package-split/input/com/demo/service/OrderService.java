package com.demo.service;

import com.demo.entity.Order;
import java.util.List;

/** Uses {@link UserService} without an import: same package before the move. */
public class OrderService {

    private final UserService users = new UserService();

    public List<Order> orders() {
        return List.of(new Order());
    }
}
