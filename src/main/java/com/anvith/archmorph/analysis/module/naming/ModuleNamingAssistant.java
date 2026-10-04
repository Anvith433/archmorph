package com.anvith.archmorph.analysis.module.naming;

import java.util.List;
import java.util.Optional;

/**
 * Extension point for an optional AI provider that can suggest module names
 * and explanations. The core pipeline never depends on it for correctness:
 * the default implementation returns nothing and deterministic naming is used.
 */
public interface ModuleNamingAssistant {

    /** A suggested name for a module with the given classes, if the assistant has one. */
    Optional<ModuleNameSuggestion> suggestName(String currentName, List<String> classNames);

    /** Suggestion produced by an assistant; always shown to the user as a suggestion, never auto-applied. */
    record ModuleNameSuggestion(String name, String explanation, double confidence) {
    }
}
