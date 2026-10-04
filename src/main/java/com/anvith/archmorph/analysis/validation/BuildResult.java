package com.anvith.archmorph.analysis.validation;

import java.util.List;

/**
 * Captured result of the sandboxed Maven run. stdout/stderr are truncated and have absolute
 * paths replaced.
 */
public record BuildResult(List<String> command, int exitCode, String stdout, String stderr, long durationMillis,
                          boolean timedOut, boolean truncated) {
}
