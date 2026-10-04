package com.clinic.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.HashSet;
import java.util.Set;

@Entity
public class User {
    @Id
    private String username;
    @OneToMany(mappedBy = "user")
    private Set<Role> roles = new HashSet<>();

    public String getUsername() { return username; }
    public Set<Role> getRoles() { return roles; }

    public void addRole(String name) {
        Role role = new Role();
        role.setName(name);
        role.setUser(this);
        roles.add(role);
    }
}
