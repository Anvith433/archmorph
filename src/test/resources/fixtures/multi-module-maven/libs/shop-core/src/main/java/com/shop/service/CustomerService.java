package com.shop.service;

import com.shop.dto.CustomerDto;
import com.shop.entity.Customer;
import com.shop.repository.CustomerRepository;

public class CustomerService {
    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public CustomerDto create(CustomerDto dto) {
        Customer entity = new Customer();
        entity.setId(dto.id);
        entity.setName(dto.name);
        repository.save(entity);
        return dto;
    }

    public int count() {
        return repository.findAll().size();
    }
}
