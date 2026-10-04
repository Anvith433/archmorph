package com.demo.service;

import com.demo.entity.User;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    public User find() {
        return new User();
    }
}
