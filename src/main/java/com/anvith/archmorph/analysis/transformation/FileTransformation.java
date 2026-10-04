package com.anvith.archmorph.analysis.transformation;

import com.anvith.archmorph.analysis.transformation.rewrite.RewriteResult;

import java.util.List;

/** Per-file outcome of executing (or dry-running) a plan entry. Paths are project-relative. */
public record FileTransformation(
        String entryId,
        String sourcePath,
        String targetPath,
        boolean changed,
        boolean packageChanged,
        List<RewriteResult.ImportChange> importChanges,
        int qualifiedRewrites,
        int linesAdded,
        int linesRemoved,
        List<String> warnings) {
}
