package com.anvith.archmorph.analysis.module.boundary;

import com.anvith.archmorph.analysis.module.editing.ModuleEdit;

import java.util.List;

/**
 * A concrete proposal for breaking one module-to-module dependency that closes a cycle.
 *
 * @param from            module whose classes depend on {@code to} (for a facade: the facade's module)
 * @param to              module that is depended on (for a facade: the modules it reaches)
 * @param subject         qualified name of the class the suggestion is about (moved class, facade), or null
 * @param dependencyCount number of class-level dependencies from {@code from} to {@code to}
 * @param steps           what to change, naming the classes, fields and methods involved
 * @param evidence        the dependencies that would disappear (class → class, kind, location)
 * @param edit            a module edit that applies the suggestion, or null when it needs a code change
 */
public record BoundarySuggestion(String id, SuggestionKind kind, String from, String to, String subject, String title,
                                 String rationale, int dependencyCount, List<String> steps, List<String> evidence,
                                 ModuleEdit edit) {

    BoundarySuggestion withId(String newId) {
        return new BoundarySuggestion(newId, kind, from, to, subject, title, rationale, dependencyCount, steps, evidence, edit);
    }
}
