package com.clinic.rest.controller.v1;

import com.clinic.model.Pet;
import com.clinic.rest.controller.BindingErrorsResponse;
import com.clinic.service.ClinicService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pets")
public class PetRestControllerV1 {
    private final ClinicService clinicService;

    public PetRestControllerV1(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    @GetMapping("/{petId}")
    public Object getPet(@PathVariable int petId) {
        Pet pet = clinicService.findPetById(petId);
        if (pet == null) {
            BindingErrorsResponse errors = new BindingErrorsResponse();
            errors.addError("petId", "not found");
            return errors;
        }
        return pet.getName() + " (" + pet.getType().getName() + ")";
    }
}
