package com.demo.service;

import com.demo.entity.User;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    public User find(Long id) {
        return new User();
    }
}
