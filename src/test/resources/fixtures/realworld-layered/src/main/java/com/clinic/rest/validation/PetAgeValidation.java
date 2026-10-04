package com.clinic.rest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Referenced by its fully-qualified name from openapi.yml (generated DTOs carry it). */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PetAgeValidator.class)
public @interface PetAgeValidation {
    String message() default "invalid birth date";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
