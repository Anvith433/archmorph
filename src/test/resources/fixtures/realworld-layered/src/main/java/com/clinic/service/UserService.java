package com.clinic.service;

import com.clinic.model.User;
import com.clinic.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void saveUser(User user) {
        if (user.getRoles().isEmpty()) {
            user.addRole("OWNER_ADMIN");
        }
        userRepository.save(user);
    }
}
