package com.anvith.archmorph.api.dto;

import java.time.Instant;
import java.util.List;

/** Project and job views. */
public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ErrorInfo(String code, String message, String hint) {
    }

    public record Capabilities(boolean canEditModules, boolean canTransform, boolean canDownload,
                               boolean hasPlan, boolean hasValidation, boolean manualReviewRequired) {
    }

    public record ProjectDto(String projectId, String name, String status, Instant createdAt, Instant expiresAt,
                             long archiveBytes, Capabilities capabilities, ErrorInfo failure, String latestJobId,
                             String latestJobStatus, List<String> warnings) {
    }

    public record JobEventDto(Instant timestamp, String event, String detail) {
    }

    public record JobDto(String jobId, String projectId, String type, String status, Instant createdAt,
                         Instant startedAt, Instant finishedAt, ErrorInfo error, List<JobEventDto> events) {
    }

    /** Response of the upload endpoint. */
    public record CreatedProjectDto(String projectId, String jobId, String status) {
    }
}
