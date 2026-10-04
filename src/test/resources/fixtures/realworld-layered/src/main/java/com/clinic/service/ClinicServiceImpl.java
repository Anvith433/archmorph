package com.clinic.service;

import com.clinic.model.Owner;
import com.clinic.model.Pet;
import com.clinic.model.Vet;
import com.clinic.repository.OwnerRepository;
import com.clinic.repository.PetRepository;
import com.clinic.repository.VetRepository;
import java.util.Collection;
import org.springframework.stereotype.Service;

@Service
public class ClinicServiceImpl implements ClinicService {
    private final OwnerRepository ownerRepository;
    private final PetRepository petRepository;
    private final VetRepository vetRepository;

    public ClinicServiceImpl(OwnerRepository ownerRepository, PetRepository petRepository, VetRepository vetRepository) {
        this.ownerRepository = ownerRepository;
        this.petRepository = petRepository;
        this.vetRepository = vetRepository;
    }

    @Override
    public Owner findOwnerById(int id) {
        return ownerRepository.findById(id);
    }

    @Override
    public Pet findPetById(int id) {
        return petRepository.findById(id);
    }

    @Override
    public Collection<Vet> findVets() {
        return vetRepository.findAll();
    }

    @Override
    public void savePet(Pet pet) {
        petRepository.save(pet);
    }
}
