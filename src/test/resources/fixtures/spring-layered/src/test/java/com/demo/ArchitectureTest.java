package com.demo;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.demo.service.OrderService;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    @Test
    void orderServiceClassExists() {
        assertNotNull(OrderService.class);
    }
}
