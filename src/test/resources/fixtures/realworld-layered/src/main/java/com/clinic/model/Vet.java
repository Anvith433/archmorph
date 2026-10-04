package com.clinic.model;

import jakarta.persistence.Entity;

@Entity
public class Vet extends Person {
    private boolean available;

    public boolean isAvailable() { return available; }
}
