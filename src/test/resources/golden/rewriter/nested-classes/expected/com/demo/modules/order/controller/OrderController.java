package com.demo.modules.order.controller;

import static com.demo.modules.order.service.OrderService.Status.PAID;

import com.demo.modules.order.service.OrderService;
import com.demo.modules.order.service.OrderService.Summary;

public class OrderController {
    private final Summary summary = new Summary();
    private final OrderService.Status status = PAID;
    private final com.demo.modules.order.service.OrderService.Summary qualified = new com.demo.modules.order.service.OrderService.Summary();
}
