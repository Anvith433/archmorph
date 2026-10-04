package com.anvith.archmorph.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned by the API.
 * The HTTP status lives with the code so handlers never guess.
 */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_ARCHIVE(HttpStatus.BAD_REQUEST),
    UNSAFE_ARCHIVE_ENTRY(HttpStatus.BAD_REQUEST),
    ARCHIVE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    ARCHIVE_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT),
    INVALID_PROJECT_STRUCTURE(HttpStatus.UNPROCESSABLE_CONTENT),
    SOURCE_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT),
    JAVA_PARSE_ERROR(HttpStatus.UNPROCESSABLE_CONTENT),
    ANALYSIS_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT),
    TIMEOUT(HttpStatus.UNPROCESSABLE_CONTENT),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_STATE(HttpStatus.CONFLICT),
    INVALID_MODULE_OPERATION(HttpStatus.BAD_REQUEST),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    TOO_MANY_JOBS(HttpStatus.TOO_MANY_REQUESTS),
    WORKSPACE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    EXTRACTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    TRANSFORMATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
