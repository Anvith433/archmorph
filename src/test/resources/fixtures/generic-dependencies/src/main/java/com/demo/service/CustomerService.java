package com.demo.service;

import org.springframework.stereotype.Service;

@Service
public class CustomerService implements Auditable {

    @Override
    public String auditName() {
        return "customer";
    }
}
