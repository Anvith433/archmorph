package com.demo.modules.order.service;

public class OrderService {

    public enum Status { NEW, PAID }

    public static class Summary {
        public Status status = Status.NEW;
    }
}

class OrderHelper {
}
