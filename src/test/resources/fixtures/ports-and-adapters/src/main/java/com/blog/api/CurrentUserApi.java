package com.blog.api;

import com.blog.application.user.UserService;
import com.blog.core.user.User;

public class CurrentUserApi {
    private final UserService service;

    public CurrentUserApi(UserService service) {
        this.service = service;
    }

    public User me(String id) {
        return service.current(id);
    }
}
