package com.anvith.archmorph.analysis.validation;

import java.util.List;

/** Outcome of one validation level. Issues are capped; {@code issueCount} is the real total. */
public record LevelResult(ValidationLevel level, ValidationStatus status, String summary, int issueCount,
                          List<ValidationIssue> issues, long durationMillis) {

    public static final int MAX_ISSUES = 200;

    public static LevelResult of(ValidationLevel level, List<ValidationIssue> issues, String passSummary, long millis) {
        boolean errors = issues.stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
        boolean warnings = issues.stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.WARNING);
        ValidationStatus status = errors ? ValidationStatus.FAIL : warnings ? ValidationStatus.WARN : ValidationStatus.PASS;
        long errorCount = issues.stream().filter(i -> i.severity() == ValidationIssue.Severity.ERROR).count();
        String summary = status == ValidationStatus.PASS ? passSummary
                : errorCount + " error(s), " + (issues.size() - errorCount) + " warning(s)";
        return new LevelResult(level, status, summary, issues.size(),
                issues.size() > MAX_ISSUES ? List.copyOf(issues.subList(0, MAX_ISSUES)) : List.copyOf(issues), millis);
    }

    public static LevelResult skipped(ValidationLevel level, String reason) {
        return new LevelResult(level, ValidationStatus.SKIPPED, reason, 0, List.of(), 0);
    }
}
