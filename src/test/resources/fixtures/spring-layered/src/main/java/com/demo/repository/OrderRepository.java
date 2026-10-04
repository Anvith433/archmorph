package com.demo.repository;

import com.demo.entity.Order;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final Map<Long, Order> store = new HashMap<>();

    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public Order save(Order entity) {
        store.put(entity.getId(), entity);
        return entity;
    }

    public List<Order> findAll() {
        return new ArrayList<>(store.values());
    }
}
