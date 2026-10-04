package com.demo.domain.repository;

import com.demo.domain.model.*;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    public User find(Long id) {
        return new User();
    }
}
