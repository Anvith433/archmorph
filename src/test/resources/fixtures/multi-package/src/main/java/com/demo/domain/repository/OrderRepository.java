package com.demo.domain.repository;

import com.demo.domain.model.*;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
    public Order find(Long id) {
        return new Order();
    }
}
