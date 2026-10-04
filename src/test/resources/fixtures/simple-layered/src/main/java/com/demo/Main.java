package com.demo;

import com.demo.controller.OrderController;
import com.demo.controller.UserController;
import com.demo.repository.OrderRepository;
import com.demo.repository.UserRepository;
import com.demo.service.OrderService;
import com.demo.service.UserService;

public class Main {

    public static void main(String[] args) {
        UserController users = new UserController(new UserService(new UserRepository()));
        OrderController orders = new OrderController(new OrderService(new OrderRepository()));
        System.out.println(users != null && orders != null);
    }
}
