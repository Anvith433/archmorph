package com.demo.modules.user.service;

import java.util.List;
import java.util.Map;

/**
 * Works with {@link com.demo.modules.user.entity.User} instances.
 */
@com.demo.shared.common.Audited
public class UserService {

    private com.demo.modules.user.entity.User current;
    private final Map<String, List<com.demo.modules.user.entity.User>> byRole = new java.util.HashMap<>();

    public com.demo.modules.user.entity.User create() {
        com.demo.modules.user.entity.User user = new com.demo.modules.user.entity.User();
        Object raw = user;
        if (raw instanceof com.demo.modules.user.entity.User typed && com.demo.shared.common.Limits.MAX > 0) {
            return (com.demo.modules.user.entity.User) typed;
        }
        return user;
    }

    public Class<?> type() {
        return com.demo.modules.user.entity.User.class;
    }
}
