package com.anvith.archmorph.analysis.dependency.resolve;

/**
 * Result of resolving a type name in the context of a compilation unit.
 *
 * @param qualifiedName         canonical name, or null when unresolvable
 * @param topLevelQualifiedName owning top-level project type (internal types only)
 * @param internal              declared in the analysed project
 * @param confidence            0..1
 * @param resolution            how it was resolved (for evidence)
 */
public record ResolvedType(String qualifiedName, String topLevelQualifiedName, boolean internal,
                           double confidence, Resolution resolution) {

    public enum Resolution {
        FULLY_QUALIFIED,
        DECLARED_IN_FILE,
        SINGLE_IMPORT,
        SAME_PACKAGE,
        WILDCARD_IMPORT,
        SYMBOL_SOLVER,
        SIMPLE_NAME_FALLBACK,
        EXTERNAL,
        UNRESOLVED
    }

    public static ResolvedType unresolved() {
        return new ResolvedType(null, null, false, 0, Resolution.UNRESOLVED);
    }

    public static ResolvedType external(String qualifiedName) {
        return new ResolvedType(qualifiedName, null, false, 1.0, Resolution.EXTERNAL);
    }
}
