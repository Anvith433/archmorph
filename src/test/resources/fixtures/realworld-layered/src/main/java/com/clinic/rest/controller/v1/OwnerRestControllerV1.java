package com.clinic.rest.controller.v1;

import com.clinic.model.Owner;
import com.clinic.rest.controller.BindingErrorsResponse;
import com.clinic.service.ClinicService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owners")
public class OwnerRestControllerV1 {
    private final ClinicService clinicService;

    public OwnerRestControllerV1(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    @GetMapping("/{ownerId}")
    public Object getOwner(@PathVariable int ownerId) {
        Owner owner = clinicService.findOwnerById(ownerId);
        if (owner == null) {
            BindingErrorsResponse errors = new BindingErrorsResponse();
            errors.addError("ownerId", "not found");
            return errors;
        }
        return owner.getFirstName() + " " + owner.getLastName() + " owns " + owner.getPets().size() + " pets";
    }
}
