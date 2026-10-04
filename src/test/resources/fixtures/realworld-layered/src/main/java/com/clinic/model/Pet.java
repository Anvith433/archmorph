package com.clinic.model;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

@Entity
public class Pet extends NamedEntity {
    @ManyToOne
    private Owner owner;
    @ManyToOne
    private PetType type;

    public Owner getOwner() { return owner; }
    public void setOwner(Owner owner) { this.owner = owner; }
    public PetType getType() { return type; }
    public void setType(PetType type) { this.type = type; }
}
