package com.shop.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Data;

@Data
@Entity
@Table(name = "orders")
public class Order {
    @Id
    private Long id;
    private BigDecimal total;
    @ManyToOne
    private Customer customer;
}
