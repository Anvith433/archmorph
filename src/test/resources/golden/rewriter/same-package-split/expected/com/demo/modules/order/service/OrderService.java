package com.demo.modules.order.service;

import com.demo.modules.order.entity.Order;
import com.demo.modules.user.service.UserService;
import java.util.List;

/** Uses {@link UserService} without an import: same package before the move. */
public class OrderService {

    private final UserService users = new UserService();

    public List<Order> orders() {
        return List.of(new Order());
    }
}
