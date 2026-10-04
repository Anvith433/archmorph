package com.clinic.repository;

import com.clinic.model.Owner;
import java.util.Collection;

public interface OwnerRepository {
    Owner findById(int id);

    Collection<Owner> findAll();

    void save(Owner owner);
}
