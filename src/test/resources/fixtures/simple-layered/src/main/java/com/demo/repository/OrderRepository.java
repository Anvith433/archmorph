package com.demo.repository;

import com.demo.entity.Order;
import java.util.ArrayList;
import java.util.List;

public class OrderRepository {
    private final List<Order> items = new ArrayList<>();

    public void save(Order item) {
        items.add(item);
    }

    public List<Order> findAll() {
        return items;
    }
}
