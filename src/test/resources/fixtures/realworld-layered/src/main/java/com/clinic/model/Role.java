package com.clinic.model;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

@Entity
public class Role extends BaseEntity {
    @ManyToOne
    private User user;
    private String name;

    public void setUser(User user) { this.user = user; }
    public User getUser() { return user; }
    public void setName(String name) { this.name = name; }
    public String getName() { return name; }
}
