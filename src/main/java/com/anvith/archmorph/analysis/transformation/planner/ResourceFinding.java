package com.anvith.archmorph.analysis.transformation.planner;

/**
 * A resource file that mentions a moved package or class. ArchMorph does not rewrite
 * configuration automatically; it reports these for review.
 *
 * @param file      project-relative path
 * @param line      1-based line
 * @param reference the old package or class name found
 * @param snippet   trimmed excerpt of the line (truncated)
 */
public record ResourceFinding(String file, int line, String reference, String snippet) {
}
