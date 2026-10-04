package com.clinic.rest.controller;

import java.util.ArrayList;
import java.util.List;

public class BindingErrorsResponse {
    private final List<String> errors = new ArrayList<>();

    public void addError(String field, String message) {
        errors.add(field + ": " + message);
    }

    public List<String> getErrors() { return errors; }
}
