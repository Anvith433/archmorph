package com.clinic.model;

import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Owner extends Person {
    @OneToMany(mappedBy = "owner")
    private List<Pet> pets = new ArrayList<>();

    public List<Pet> getPets() { return pets; }

    public void addPet(Pet pet) {
        pets.add(pet);
        pet.setOwner(this);
    }
}
