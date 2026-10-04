package com.demo.service;

import com.demo.common.Page;
import com.demo.entity.Customer;
import com.demo.entity.Order;
import com.demo.entity.Product;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final List<Product> products = new java.util.ArrayList<>();
    private final Map<String, List<Order>> ordersByCustomer = new java.util.HashMap<>();

    public Page<Product> page() {
        Page<Product> page = new Page<>();
        page.content = products;
        return page;
    }

    public Optional<Customer> customerOf(Order order) {
        return Optional.empty();
    }

    public Map<String, List<Order>> orders() {
        return ordersByCustomer;
    }
}
