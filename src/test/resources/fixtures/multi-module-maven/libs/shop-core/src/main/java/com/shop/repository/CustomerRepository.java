package com.shop.repository;

import com.shop.entity.Customer;
import java.util.ArrayList;
import java.util.List;

public class CustomerRepository {
    private final List<Customer> items = new ArrayList<>();

    public void save(Customer item) {
        items.add(item);
    }

    public List<Customer> findAll() {
        return items;
    }
}
