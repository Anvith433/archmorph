package com.shop.repository;

import com.shop.model.Customer;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {
    private final Map<Long, Customer> customers = new HashMap<>();

    public Customer find(long id) {
        return customers.get(id);
    }
}
