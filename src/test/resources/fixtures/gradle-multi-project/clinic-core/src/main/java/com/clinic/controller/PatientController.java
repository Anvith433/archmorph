package com.clinic.controller;

import com.clinic.dto.PatientDto;
import com.clinic.service.PatientService;

public class PatientController {
    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    public PatientDto create(PatientDto dto) {
        return service.create(dto);
    }
}
