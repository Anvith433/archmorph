package com.anvith.archmorph.analysis.module.editing;

import java.util.List;

/**
 * One user decision on the module assignment. Decisions are stored as an
 * ordered list and replayed on top of ArchMorph's suggestion, so the review UI
 * can show suggestion, user decisions and the resulting final plan side by side.
 *
 * <pre>
 * RENAME_MODULE   module, newName
 * MERGE_MODULES   sources (modules), target (module)
 * SPLIT_MODULE    module, newName (new module), classes (qualified names to move out)
 * MOVE_CLASS      className, target (module)
 * MOVE_TO_SHARED  className
 * EXCLUDE_CLASS   className      (file stays where it is)
 * INCLUDE_CLASS   className
 * LOCK_CLASS      className
 * UNLOCK_CLASS    className
 * </pre>
 */
public record ModuleEdit(Type type, String module, String newName, String target, List<String> sources,
                         String className, List<String> classes) {

    public enum Type {
        RENAME_MODULE,
        MERGE_MODULES,
        SPLIT_MODULE,
        MOVE_CLASS,
        MOVE_TO_SHARED,
        EXCLUDE_CLASS,
        INCLUDE_CLASS,
        LOCK_CLASS,
        UNLOCK_CLASS
    }
}
