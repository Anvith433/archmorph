package com.anvith.archmorph.analysis.module;

import java.util.List;

/**
 * The module decision for one top-level class: what ArchMorph suggested,
 * what the user changed, and why.
 *
 * @param qualifiedName fully-qualified name of the top-level class
 * @param moduleName    business module, or "shared" / "application"
 * @param category      placement category
 * @param confidence    0..1 confidence of this placement
 * @param origin        AUTOMATIC suggestion or USER decision
 * @param locked        user locked the class: later operations may not move it
 * @param excluded      excluded from transformation; the file stays where it is
 * @param reasons       human-readable evidence
 */
public record ClassAssignment(String qualifiedName, String moduleName, ModuleCategory category, double confidence,
                              Origin origin, boolean locked, boolean excluded, List<String> reasons) {

    public enum Origin {
        AUTOMATIC,
        USER
    }

    public ClassAssignment withPlacement(String module, ModuleCategory newCategory, double newConfidence,
                                         Origin newOrigin, List<String> newReasons) {
        return new ClassAssignment(qualifiedName, module, newCategory, newConfidence, newOrigin, locked, excluded, newReasons);
    }

    public ClassAssignment withLocked(boolean value) {
        return new ClassAssignment(qualifiedName, moduleName, category, confidence, origin, value, excluded, reasons);
    }

    public ClassAssignment withExcluded(boolean value) {
        return new ClassAssignment(qualifiedName, moduleName, category, confidence, origin, locked, value, reasons);
    }
}
