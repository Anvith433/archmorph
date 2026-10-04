package com.clinic.controller;

import com.clinic.dto.AppointmentDto;
import com.clinic.service.AppointmentService;

public class AppointmentController {
    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    public AppointmentDto create(AppointmentDto dto) {
        return service.create(dto);
    }
}
