package com.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.demo.dto.UserDto;
import com.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;

class UserServiceTest {

    @Test
    void createsAndReadsUser() {
        UserService service = new UserService(new UserRepository());
        UserDto dto = new UserDto();
        dto.setId(1L);
        dto.setName("Ada");
        service.create(dto);
        assertEquals("Ada", service.get(1L).getName());
    }
}
