package com.clinic.model;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public class BaseEntity {
    @Id
    @GeneratedValue
    protected Integer id;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public boolean isNew() { return this.id == null; }
}
