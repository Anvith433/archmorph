package com.blog.application.user;

import com.blog.core.service.JwtService;
import com.blog.core.user.User;
import com.blog.core.user.UserRepository;

public class UserService {
    private final UserRepository users;
    private final JwtService jwt;

    public UserService(UserRepository users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    public String register(String id, String username) {
        User user = new User(id, username);
        users.save(user);
        return jwt.toToken(user);
    }

    public User current(String id) {
        return users.findById(id).orElseThrow();
    }
}
