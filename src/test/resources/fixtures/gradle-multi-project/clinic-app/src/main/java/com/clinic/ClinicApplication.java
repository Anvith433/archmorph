package com.clinic;

import com.clinic.controller.PatientController;
import com.clinic.repository.PatientRepository;
import com.clinic.service.PatientService;
import com.clinic.controller.AppointmentController;
import com.clinic.repository.AppointmentRepository;
import com.clinic.service.AppointmentService;

public class ClinicApplication {

    public static void main(String[] args) {
        PatientController patient = new PatientController(new PatientService(new PatientRepository()));
        AppointmentController appointment = new AppointmentController(new AppointmentService(new AppointmentRepository()));
        System.out.println("started");
    }
}
