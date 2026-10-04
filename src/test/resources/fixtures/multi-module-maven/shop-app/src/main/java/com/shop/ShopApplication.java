package com.shop;

import com.shop.controller.OrderController;
import com.shop.repository.OrderRepository;
import com.shop.service.OrderService;
import com.shop.controller.CustomerController;
import com.shop.repository.CustomerRepository;
import com.shop.service.CustomerService;

public class ShopApplication {

    public static void main(String[] args) {
        OrderController order = new OrderController(new OrderService(new OrderRepository()));
        CustomerController customer = new CustomerController(new CustomerService(new CustomerRepository()));
        System.out.println("started");
    }
}
