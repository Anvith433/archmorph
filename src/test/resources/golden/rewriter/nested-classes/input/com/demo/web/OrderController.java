package com.demo.web;

import static com.demo.service.OrderService.Status.PAID;

import com.demo.service.OrderService;
import com.demo.service.OrderService.Summary;

public class OrderController {
    private final Summary summary = new Summary();
    private final OrderService.Status status = PAID;
    private final com.demo.service.OrderService.Summary qualified = new com.demo.service.OrderService.Summary();
}
