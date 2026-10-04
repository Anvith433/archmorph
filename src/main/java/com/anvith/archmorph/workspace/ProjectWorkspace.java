package com.anvith.archmorph.workspace;

import com.anvith.archmorph.common.exception.ArchMorphException;
import com.anvith.archmorph.common.exception.ErrorCode;

import java.nio.file.Path;

/**
 * Isolated directory tree of one project:
 *
 * <pre>
 * workspace/projects/&lt;projectId&gt;/
 *     input/        uploaded archive (never modified)
 *     original/     extracted project (read-only for the pipeline)
 *     transformed/  generated modular project
 *     dry-run/      dry-run artefacts
 *     reports/      JSON + Markdown reports
 *     metadata/     project metadata
 *     build-home/   HOME for sandboxed Maven runs
 * </pre>
 */
public record ProjectWorkspace(String projectId, Path root) {

    public Path input() {
        return root.resolve("input");
    }

    public Path archive() {
        return input().resolve("archive.zip");
    }

    public Path original() {
        return root.resolve("original");
    }

    public Path transformed() {
        return root.resolve("transformed");
    }

    public Path dryRun() {
        return root.resolve("dry-run");
    }

    public Path reports() {
        return root.resolve("reports");
    }

    public Path metadata() {
        return root.resolve("metadata");
    }

    public Path buildHome() {
        return root.resolve("build-home");
    }

    /**
     * Resolve a relative path inside {@code base}, rejecting anything that
     * would escape it (absolute paths, "..", symlink tricks after normalisation).
     */
    public static Path resolveInside(Path base, String relative) {
        if (relative == null || relative.isBlank() || relative.indexOf('\0') >= 0) {
            throw new ArchMorphException(ErrorCode.INVALID_REQUEST, "Invalid path.");
        }
        String normalized = relative.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new ArchMorphException(ErrorCode.INVALID_REQUEST, "Invalid path.");
        }
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path resolved = normalizedBase.resolve(normalized).normalize();
        if (!resolved.startsWith(normalizedBase) || resolved.equals(normalizedBase)) {
            throw new ArchMorphException(ErrorCode.INVALID_REQUEST, "Invalid path.");
        }
        return resolved;
    }
}
