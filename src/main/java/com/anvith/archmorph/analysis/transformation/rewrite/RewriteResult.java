package com.anvith.archmorph.analysis.transformation.rewrite;

import java.util.List;

/**
 * Outcome of rewriting one file.
 *
 * @param source              rewritten source text
 * @param changed             whether the text differs from the input
 * @param importChanges       import rewrites, additions and removals
 * @param qualifiedRewrites   number of fully-qualified references rewritten
 * @param packageChanged      whether the package declaration changed
 * @param warnings            issues the rewrite could not resolve
 */
public record RewriteResult(String source, boolean changed, List<ImportChange> importChanges,
                            int qualifiedRewrites, boolean packageChanged, List<String> warnings) {

    public enum ChangeKind {
        REWRITTEN,
        ADDED,
        REMOVED_OBSOLETE,
        REMOVED_WILDCARD
    }

    public record ImportChange(ChangeKind kind, String from, String to) {
    }
}
