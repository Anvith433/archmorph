package com.clinic.service;

import com.clinic.dto.PatientDto;
import com.clinic.entity.Patient;
import com.clinic.repository.PatientRepository;

public class PatientService {
    private final PatientRepository repository;

    public PatientService(PatientRepository repository) {
        this.repository = repository;
    }

    public PatientDto create(PatientDto dto) {
        Patient entity = new Patient();
        entity.setId(dto.id);
        entity.setName(dto.name);
        repository.save(entity);
        return dto;
    }

    public int count() {
        return repository.findAll().size();
    }
}
