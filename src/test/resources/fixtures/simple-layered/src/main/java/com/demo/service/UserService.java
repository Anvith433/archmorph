package com.demo.service;

import com.demo.dto.UserDto;
import com.demo.entity.User;
import com.demo.repository.UserRepository;

public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public UserDto create(UserDto dto) {
        User entity = new User();
        entity.setId(dto.id);
        entity.setName(dto.name);
        repository.save(entity);
        return dto;
    }
}
