package com.demo.repository;

import com.demo.entity.Payment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {

    private final Map<Long, Payment> store = new HashMap<>();

    public Optional<Payment> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public Payment save(Payment entity) {
        store.put(entity.getId(), entity);
        return entity;
    }

    public List<Payment> findAll() {
        return new ArrayList<>(store.values());
    }
}
