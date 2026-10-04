package com.anvith.archmorph.analysis.transformation.planner;

import java.util.List;

/**
 * A conflict found while planning. Colliding files are never overwritten:
 * they are kept in place and reported for review.
 *
 * @param type       collision kind
 * @param target     the contested path or qualified name
 * @param sources    source files (project-relative) involved
 * @param resolution what the planner did about it
 */
public record PlanConflict(Type type, String target, List<String> sources, String resolution) {

    public enum Type {
        TARGET_PATH_COLLISION,
        TARGET_CLASS_NAME_COLLISION,
        DEFAULT_PACKAGE_DEPENDENCY
    }
}
