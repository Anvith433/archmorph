package com.demo.service;

import com.demo.common.PageResult;
import com.demo.entity.User;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    public PageResult<User> page() {
        PageResult<User> result = new PageResult<>();
        result.items = List.of();
        return result;
    }
}
