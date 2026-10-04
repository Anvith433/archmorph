package com.demo.controller;

import static com.demo.service.OrderService.Status.NEW;

import com.demo.service.OrderService;
import com.demo.service.OrderService.Summary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;
    private final OrderService.Config config = new OrderService.Config();
    private final com.demo.service.OrderService.Config qualifiedConfig = new com.demo.service.OrderService.Config();

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    public Summary get() {
        OrderService.Status status = NEW;
        return status == NEW ? service.summarize(config.retries + qualifiedConfig.retries) : null;
    }
}
