package com.demo.controller;

import com.demo.admin.dto.UserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

    @GetMapping
    public UserResponse get() {
        return new UserResponse();
    }
}
