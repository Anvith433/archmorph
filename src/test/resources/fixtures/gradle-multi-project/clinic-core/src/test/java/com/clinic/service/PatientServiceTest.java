package com.clinic.service;

import com.clinic.dto.PatientDto;
import com.clinic.repository.PatientRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatientServiceTest {

    @Test
    void create() {
        PatientService service = new PatientService(new PatientRepository());
        PatientDto dto = new PatientDto();
        dto.name = "first";
        service.create(dto);
        assertEquals(1, service.count());
    }
}
