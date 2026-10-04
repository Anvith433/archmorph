package com.shop.repository;

import com.shop.model.Order;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
    private final Map<Long, Order> orders = new HashMap<>();

    public Order find(long id) {
        return orders.get(id);
    }

    public void save(Order order) {
        orders.put(order.getId(), order);
    }
}
