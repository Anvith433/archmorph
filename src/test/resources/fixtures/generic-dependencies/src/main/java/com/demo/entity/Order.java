package com.demo.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import java.util.List;

@Entity
public class Order {
    @Id
    private Long id;

    @ManyToOne
    private Customer customer;

    @ManyToMany
    private List<Product> products;
}
