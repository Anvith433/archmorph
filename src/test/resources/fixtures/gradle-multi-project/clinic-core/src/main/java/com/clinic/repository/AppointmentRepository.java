package com.clinic.repository;

import com.clinic.entity.Appointment;
import java.util.ArrayList;
import java.util.List;

public class AppointmentRepository {
    private final List<Appointment> items = new ArrayList<>();

    public void save(Appointment item) {
        items.add(item);
    }

    public List<Appointment> findAll() {
        return items;
    }
}
