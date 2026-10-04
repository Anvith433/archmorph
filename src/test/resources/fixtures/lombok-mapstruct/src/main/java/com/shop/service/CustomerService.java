package com.shop.service;

import com.shop.model.Customer;
import com.shop.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {
    private final CustomerRepository customers;

    public Customer find(long id) {
        return customers.find(id);
    }
}
