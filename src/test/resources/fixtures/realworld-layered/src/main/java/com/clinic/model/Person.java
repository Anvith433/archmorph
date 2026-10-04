package com.clinic.model;

import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public class Person extends BaseEntity {
    protected String firstName;
    protected String lastName;

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
}
