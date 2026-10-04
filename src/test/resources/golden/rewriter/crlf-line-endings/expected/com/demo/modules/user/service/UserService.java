package com.demo.modules.user.service;

import com.demo.modules.user.entity.User;
import com.demo.service.Role;
import java.util.List;

public class UserService {
    private List<Role> roles;

    public User get() {
        return new User();
    }
}
