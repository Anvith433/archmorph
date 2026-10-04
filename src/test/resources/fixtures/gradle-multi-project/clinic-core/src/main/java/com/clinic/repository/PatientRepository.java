package com.clinic.repository;

import com.clinic.entity.Patient;
import java.util.ArrayList;
import java.util.List;

public class PatientRepository {
    private final List<Patient> items = new ArrayList<>();

    public void save(Patient item) {
        items.add(item);
    }

    public List<Patient> findAll() {
        return items;
    }
}
