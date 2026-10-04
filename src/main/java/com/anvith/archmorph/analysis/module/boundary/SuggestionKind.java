package com.anvith.archmorph.analysis.module.boundary;

/** How a dependency that closes a module cycle can be removed. */
public enum SuggestionKind {
    /** A class sits in the wrong module; moving it removes the cycle. Can be applied as a module edit. */
    MOVE_CLASS,
    /** A bidirectional JPA association; keep the owning side, replace the inverse side with a query. */
    UNIDIRECTIONAL_RELATIONSHIP,
    /** A facade spanning several modules; split its methods so each module owns its part. */
    SPLIT_FACADE,
    /** A plain call dependency; invert it with an event or an interface owned by the calling module. */
    INVERT_DEPENDENCY
}
