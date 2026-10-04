package com.demo.domain.service;

import com.demo.domain.model.*;
import com.demo.domain.repository.*;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public User get(Long id) {
        return repository.find(id);
    }
}
