package com.clinic.repository.jdbc;

import com.clinic.model.Pet;
import com.clinic.repository.PetRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("jdbc")
public class JdbcPetRepositoryImpl implements PetRepository {

    private final List<Pet> store = new ArrayList<>();

    @Override
    public Pet findById(int id) {
        return store.stream().filter(e -> e.getId() != null && e.getId() == id).findFirst().orElse(null);
    }

    @Override
    public Collection<Pet> findAll() {
        return store;
    }

    @Override
    public void save(Pet entity) {
        store.add(entity);
    }
}
