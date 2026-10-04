package com.anvith.archmorph.project;

/** Lifecycle of a project; the frontend renders these as its state machine. */
public enum ProjectStatus {
    QUEUED,
    ANALYZING,
    PLANNING,
    READY_FOR_REVIEW,
    TRANSFORMING,
    VALIDATING,
    COMPLETED,
    FAILED
}
