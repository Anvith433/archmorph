package com.clinic.rest.controller.v1;

import com.clinic.model.Vet;
import com.clinic.service.ClinicService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vets")
public class VetRestControllerV1 {
    private final ClinicService clinicService;

    public VetRestControllerV1(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    @GetMapping
    public List<String> listVets() {
        return clinicService.findVets().stream().filter(Vet::isAvailable).map(Vet::getLastName).toList();
    }
}
