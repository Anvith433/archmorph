package com.clinic.repository.jpa;

import com.clinic.model.Vet;
import com.clinic.repository.VetRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("jpa")
public class JpaVetRepositoryImpl implements VetRepository {

    private final List<Vet> store = new ArrayList<>();

    @Override
    public Vet findById(int id) {
        return store.stream().filter(e -> e.getId() != null && e.getId() == id).findFirst().orElse(null);
    }

    @Override
    public Collection<Vet> findAll() {
        return store;
    }

    @Override
    public void save(Vet entity) {
        store.add(entity);
    }
}
