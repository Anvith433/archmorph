package com.demo.repository;

import com.demo.entity.Payment;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {
    public Payment save(Payment payment) {
        return payment;
    }
}
