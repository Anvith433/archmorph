package com.clinic.repository;

import com.clinic.model.Vet;
import java.util.Collection;

public interface VetRepository {
    Vet findById(int id);

    Collection<Vet> findAll();

    void save(Vet vet);
}
