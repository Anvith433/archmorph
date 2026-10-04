package com.blog.graphql;

import com.blog.application.user.UserService;
import com.blog.core.user.User;

public class MeDatafetcher {
    private final UserService service;

    public MeDatafetcher(UserService service) {
        this.service = service;
    }

    public User me(String id) {
        return service.current(id);
    }
}
