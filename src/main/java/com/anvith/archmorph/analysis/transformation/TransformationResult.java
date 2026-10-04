package com.anvith.archmorph.analysis.transformation;

import java.util.List;

/**
 * Result of executing a plan.
 *
 * @param dryRun               nothing was written when true
 * @param javaFilesProcessed   Java files rewritten or copied as planned
 * @param javaFilesMoved       Java files written to a different path
 * @param resourceFilesCopied  non-Java files copied unchanged
 */
public record TransformationResult(boolean dryRun, int javaFilesProcessed, int javaFilesMoved,
                                   int resourceFilesCopied, List<FileTransformation> files, List<String> warnings,
                                   long durationMillis) {
}
