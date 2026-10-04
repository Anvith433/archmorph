package com.demo.modules.payment.service;

import java.util.List;
import java.time.Instant;
import com.demo.modules.order.service.OrderService;

public class PaymentService {
    private OrderService orders;
    private List<Instant> history;
}
