package com.anvith.archmorph.analysis.transformation.planner;

/** One declared type of a file and its qualified name before and after the plan. */
public record ClassMove(String sourceQualifiedName, String targetQualifiedName, boolean nested) {
}
