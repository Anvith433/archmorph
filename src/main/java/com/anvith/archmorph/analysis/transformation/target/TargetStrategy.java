package com.anvith.archmorph.analysis.transformation.target;

/** Target architecture strategies. Only {@code MODULAR_BY_DOMAIN} is implemented in this release. */
public enum TargetStrategy {
    MODULAR_BY_DOMAIN,
    MODULAR_BY_FEATURE,
    CUSTOM
}
