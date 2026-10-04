package com.anvith.archmorph.analysis.validation;

import java.time.Instant;
import java.util.List;

/** Complete validation outcome of a transformed project. */
public record ValidationReport(ValidationStatus status, Instant completedAt, List<LevelResult> levels,
                               BuildResult build, boolean dryRunBased) {

    public static ValidationStatus overall(List<LevelResult> levels) {
        if (levels.stream().anyMatch(l -> l.status() == ValidationStatus.FAIL)) {
            return ValidationStatus.FAIL;
        }
        if (levels.stream().anyMatch(l -> l.status() == ValidationStatus.WARN)) {
            return ValidationStatus.WARN;
        }
        return ValidationStatus.PASS;
    }
}
