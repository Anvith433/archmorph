package com.blog.api;

import com.blog.application.user.UserService;

public class UsersApi {
    private final UserService service;

    public UsersApi(UserService service) {
        this.service = service;
    }

    public String register(String id, String username) {
        return service.register(id, username);
    }
}
