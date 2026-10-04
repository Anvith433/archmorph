package com.demo.web.rest;

import com.demo.domain.model.User;
import com.demo.domain.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserResource {

    private final UserService users;

    public UserResource(UserService users) {
        this.users = users;
    }

    @GetMapping
    public User get() {
        return users.get(1L);
    }
}
