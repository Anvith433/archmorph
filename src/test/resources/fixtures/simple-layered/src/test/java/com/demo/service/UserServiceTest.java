package com.demo.service;

import com.demo.dto.UserDto;
import com.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;

class UserServiceTest {

    @Test
    void create() {
        UserDto dto = new UserDto();
        dto.name = "Ada";
        new UserService(new UserRepository()).create(dto);
    }
}
