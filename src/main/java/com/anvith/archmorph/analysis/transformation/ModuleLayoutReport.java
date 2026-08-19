package com.anvith.archmorph.analysis.transformation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleLayoutReport {

    /*
     * moduleName -> layout
     */
    private final Map<String, ModuleLayout> layouts =
            new LinkedHashMap<>();

    /**
     * Get existing layout
     * or create one.
     */
    public ModuleLayout getOrCreateLayout(
            String moduleName) {

        return layouts.computeIfAbsent(
                moduleName,
                ModuleLayout::new
        );

    }

    /**
     * All layouts.
     */
    public Collection<ModuleLayout> getLayouts() {

        return layouts.values();

    }

    public boolean isEmpty() {

        return layouts.isEmpty();

    }

    public int size() {

        return layouts.size();

    }

}