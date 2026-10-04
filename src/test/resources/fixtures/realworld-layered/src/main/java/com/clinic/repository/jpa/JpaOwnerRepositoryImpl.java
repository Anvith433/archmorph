package com.clinic.repository.jpa;

import com.clinic.model.Owner;
import com.clinic.repository.OwnerRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("jpa")
public class JpaOwnerRepositoryImpl implements OwnerRepository {

    private final List<Owner> store = new ArrayList<>();

    @Override
    public Owner findById(int id) {
        return store.stream().filter(e -> e.getId() != null && e.getId() == id).findFirst().orElse(null);
    }

    @Override
    public Collection<Owner> findAll() {
        return store;
    }

    @Override
    public void save(Owner entity) {
        store.add(entity);
    }
}
