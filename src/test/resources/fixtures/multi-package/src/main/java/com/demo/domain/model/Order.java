package com.demo.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Order {
    @Id
    private Long id;
    private Long userId;
    public Long getUserId() { return userId; }
}
