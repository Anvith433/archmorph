package com.demo.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class BaseRepository<T, ID> {
    protected final Map<ID, T> store = new HashMap<>();

    public Optional<T> find(ID id) {
        return Optional.ofNullable(store.get(id));
    }
}
