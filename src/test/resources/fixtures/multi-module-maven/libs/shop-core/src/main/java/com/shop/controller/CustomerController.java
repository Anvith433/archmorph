package com.shop.controller;

import com.shop.dto.CustomerDto;
import com.shop.service.CustomerService;

public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    public CustomerDto create(CustomerDto dto) {
        return service.create(dto);
    }
}
