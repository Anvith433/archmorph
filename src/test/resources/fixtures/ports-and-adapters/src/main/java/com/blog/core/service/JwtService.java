package com.blog.core.service;

import com.blog.core.user.User;

public interface JwtService {
    String toToken(User user);
}
