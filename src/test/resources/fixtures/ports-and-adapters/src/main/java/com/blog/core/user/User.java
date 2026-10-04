package com.blog.core.user;

public class User {
    private final String id;
    private final String username;

    public User(String id, String username) {
        this.id = id;
        this.username = username;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
}
