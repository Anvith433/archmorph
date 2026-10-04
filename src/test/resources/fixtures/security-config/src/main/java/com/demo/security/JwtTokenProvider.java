package com.demo.security;

import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    public String issue(String subject) {
        return "token-for-" + subject;
    }
}
