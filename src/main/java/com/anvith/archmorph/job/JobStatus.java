package com.anvith.archmorph.job;

public enum JobStatus {
    QUEUED,
    ANALYZING,
    PLANNING,
    TRANSFORMING,
    VALIDATING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
