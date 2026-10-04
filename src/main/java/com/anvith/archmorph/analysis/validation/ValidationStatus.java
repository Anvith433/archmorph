package com.anvith.archmorph.analysis.validation;

public enum ValidationStatus {
    PASS,
    WARN,
    FAIL,
    /** The level could not run (for example Maven is not installed); never counted as a pass. */
    SKIPPED
}
