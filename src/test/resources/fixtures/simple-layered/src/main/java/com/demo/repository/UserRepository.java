package com.demo.repository;

import com.demo.entity.User;
import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    private final List<User> items = new ArrayList<>();

    public void save(User item) {
        items.add(item);
    }

    public List<User> findAll() {
        return items;
    }
}
