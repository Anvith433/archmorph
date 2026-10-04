package com.clinic.service;

import com.clinic.dto.AppointmentDto;
import com.clinic.entity.Appointment;
import com.clinic.repository.AppointmentRepository;

public class AppointmentService {
    private final AppointmentRepository repository;

    public AppointmentService(AppointmentRepository repository) {
        this.repository = repository;
    }

    public AppointmentDto create(AppointmentDto dto) {
        Appointment entity = new Appointment();
        entity.setId(dto.id);
        entity.setName(dto.name);
        repository.save(entity);
        return dto;
    }

    public int count() {
        return repository.findAll().size();
    }
}
