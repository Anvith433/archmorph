package com.demo.controller;

import com.demo.common.ApiResponse;
import com.demo.entity.User;
import com.demo.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Object> list() {
        return new ApiResponse<>(service != null ? new User() : null);
    }
}
