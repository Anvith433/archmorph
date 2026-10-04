package com.anvith.archmorph.analysis.validation;

/**
 * One finding. Paths are project-relative; messages never contain absolute server paths.
 *
 * @param severity      ERROR makes the level fail, WARNING makes it warn
 * @param file          project-relative file, may be null
 * @param line          1-based line, 0 when unknown
 * @param probableCause what most likely caused it and what to do
 */
public record ValidationIssue(Severity severity, String file, int line, String message, String probableCause) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public static ValidationIssue error(String file, int line, String message, String cause) {
        return new ValidationIssue(Severity.ERROR, file, line, message, cause);
    }

    public static ValidationIssue warning(String file, int line, String message, String cause) {
        return new ValidationIssue(Severity.WARNING, file, line, message, cause);
    }
}
