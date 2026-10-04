package com.demo.modules.user.service;

// domain
import com.demo.modules.user.entity.User;
// jdk
import java.util.List;
import com.demo.shared.common.AuditService;

public class UserService {
    private List<User> users;
    private AuditService audit;
}
