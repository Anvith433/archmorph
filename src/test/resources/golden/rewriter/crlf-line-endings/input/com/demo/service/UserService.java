package com.demo.service;

import com.demo.entity.User;
import java.util.List;

public class UserService {
    private List<Role> roles;

    public User get() {
        return new User();
    }
}
