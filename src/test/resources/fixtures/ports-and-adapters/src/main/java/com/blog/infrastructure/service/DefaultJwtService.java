package com.blog.infrastructure.service;

import com.blog.core.service.JwtService;
import com.blog.core.user.User;

public class DefaultJwtService implements JwtService {

    @Override
    public String toToken(User user) {
        return "token-" + user.getId();
    }
}
