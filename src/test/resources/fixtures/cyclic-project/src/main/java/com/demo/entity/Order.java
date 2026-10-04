package com.demo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Order {
    @Id
    private Long id;

    @ManyToOne
    private User user;

    public User getUser() { return user; }
}
