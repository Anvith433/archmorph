package com.anvith.archmorph.report;

import java.util.Arrays;
import java.util.Optional;

/** The fixed set of report files a project can have. File names are never taken from requests. */
public enum ReportName {
    ANALYSIS_JSON("analysis.json", "application/json"),
    MODULES_JSON("modules.json", "application/json"),
    PLAN_JSON("transformation-plan.json", "application/json"),
    VALIDATION_JSON("validation.json", "application/json"),
    ANALYSIS_MD("analysis.md", "text/markdown; charset=utf-8"),
    SUMMARY_MD("transformation-summary.md", "text/markdown; charset=utf-8"),
    VALIDATION_MD("validation-report.md", "text/markdown; charset=utf-8");

    private final String fileName;
    private final String contentType;

    ReportName(String fileName, String contentType) {
        this.fileName = fileName;
        this.contentType = contentType;
    }

    public String fileName() {
        return fileName;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<ReportName> fromFileName(String name) {
        return Arrays.stream(values()).filter(r -> r.fileName.equals(name)).findFirst();
    }
}
