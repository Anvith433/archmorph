package com.anvith.archmorph.api.dto;

import java.time.Instant;
import java.util.List;

/** Validation report views. */
public final class ValidationDtos {

    private ValidationDtos() {
    }

    public record IssueDto(String severity, String file, int line, String message, String probableCause) {
    }

    public record LevelDto(String level, String label, String status, String summary, int issueCount,
                           List<IssueDto> issues, long durationMillis) {
    }

    public record BuildDto(List<String> command, int exitCode, String stdout, String stderr, long durationMillis,
                           boolean timedOut, boolean truncated) {
    }

    public record ValidationDto(String status, Instant completedAt, List<LevelDto> levels, BuildDto build) {
    }
}
