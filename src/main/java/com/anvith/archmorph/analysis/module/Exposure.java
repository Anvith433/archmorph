package com.anvith.archmorph.analysis.module;

/**
 * A user's decision about whether a class belongs to its module's public API (MODULAR_MONOLITH layout). Without a
 * decision, a class is part of the API exactly when another module uses it.
 */
public enum Exposure {
    /** Always in the module root package, even if no other module uses it yet. */
    PUBLIC_API,
    /** Always in an internal sub-package; only allowed while no other module uses the class. */
    INTERNAL
}
