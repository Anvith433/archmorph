package com.anvith.archmorph.analysis.transformation;

/**
 * What the executor does with a source file. An entry can carry several actions,
 * e.g. {@code MOVE + REWRITE_PACKAGE + REWRITE_IMPORT}.
 */
public enum TransformationAction {
    /** File is written to a different path. */
    MOVE,
    /** File is duplicated (never planned automatically; reserved for explicit user requests). */
    COPY,
    /** The package declaration changes. */
    REWRITE_PACKAGE,
    /** Imports are rewritten, added or removed. */
    REWRITE_IMPORT,
    /** Fully-qualified references in code are rewritten. */
    REWRITE_QUALIFIED_REFERENCE,
    /** File stays where it is; unchanged by a move. */
    KEEP,
    /** User excluded the class from the transformation. */
    EXCLUDE,
    /** Not transformed automatically; kept in place and reported for a human decision. */
    MANUAL_REVIEW
}
