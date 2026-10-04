package com.anvith.archmorph.analysis.validation;

/** One validation level. Implementations must not execute any code of the project. */
public interface LevelValidator {

    ValidationLevel level();

    LevelResult validate(ValidationContext context);
}
