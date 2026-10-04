package com.demo.repository;

import com.demo.entity.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final Map<Long, User> store = new HashMap<>();

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public User save(User entity) {
        store.put(entity.getId(), entity);
        return entity;
    }

    public List<User> findAll() {
        return new ArrayList<>(store.values());
    }
}
