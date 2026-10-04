package com.clinic.service;

import com.clinic.model.Owner;
import com.clinic.model.Pet;
import com.clinic.model.Vet;
import java.util.Collection;

public interface ClinicService {
    Owner findOwnerById(int id);

    Pet findPetById(int id);

    Collection<Vet> findVets();

    void savePet(Pet pet);
}
