package com.clinic.repository;

import com.clinic.model.Pet;
import java.util.Collection;

public interface PetRepository {
    Pet findById(int id);

    Collection<Pet> findAll();

    void save(Pet pet);
}
