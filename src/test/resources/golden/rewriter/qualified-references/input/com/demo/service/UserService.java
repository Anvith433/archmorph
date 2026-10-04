package com.demo.service;

import java.util.List;
import java.util.Map;

/**
 * Works with {@link com.demo.entity.User} instances.
 */
@com.demo.common.Audited
public class UserService {

    private com.demo.entity.User current;
    private final Map<String, List<com.demo.entity.User>> byRole = new java.util.HashMap<>();

    public com.demo.entity.User create() {
        com.demo.entity.User user = new com.demo.entity.User();
        Object raw = user;
        if (raw instanceof com.demo.entity.User typed && com.demo.common.Limits.MAX > 0) {
            return (com.demo.entity.User) typed;
        }
        return user;
    }

    public Class<?> type() {
        return com.demo.entity.User.class;
    }
}
