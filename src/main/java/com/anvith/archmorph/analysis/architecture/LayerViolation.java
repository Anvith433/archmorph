package com.anvith.archmorph.analysis.architecture;

import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.analysis.dependency.SourceLocation;
import com.anvith.archmorph.parser.ComponentType;

/** A dependency that breaks a layer rule. */
public record LayerViolation(
        String source,
        String sourceClassName,
        ComponentType sourceType,
        String target,
        String targetClassName,
        ComponentType targetType,
        DependencyType dependencyType,
        SourceLocation location,
        Severity severity,
        String rationale) {

    public String message() {
        return String.format("%s '%s' must not depend on %s '%s'",
                sourceType, sourceClassName, targetType, targetClassName);
    }
}
