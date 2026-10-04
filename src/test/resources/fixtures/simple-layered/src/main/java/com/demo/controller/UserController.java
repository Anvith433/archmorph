package com.demo.controller;

import com.demo.dto.UserDto;
import com.demo.service.UserService;

public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    public UserDto create(UserDto dto) {
        return service.create(dto);
    }
}
