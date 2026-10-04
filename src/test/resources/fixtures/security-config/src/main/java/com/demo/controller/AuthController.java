package com.demo.controller;

import com.demo.dto.LoginRequest;
import com.demo.security.JwtTokenProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtTokenProvider provider;

    public AuthController(JwtTokenProvider provider) {
        this.provider = provider;
    }

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest request) {
        return provider.issue(request.username);
    }
}
