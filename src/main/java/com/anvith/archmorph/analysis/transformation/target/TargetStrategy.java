package com.anvith.archmorph.analysis.transformation.target;

/** Target architecture strategies. */
public enum TargetStrategy {

    /**
     * Modular monolith following the Spring Modulith conventions: every business module is a direct
     * sub-package of the application package; the module package itself holds the module's public API
     * (the types other modules use), its sub-packages hold the internals.
     */
    MODULAR_MONOLITH,

    /** Package-by-module layout {@code <base>.modules.<module>.<layer>} without API/internal separation. */
    MODULAR_BY_DOMAIN
}
