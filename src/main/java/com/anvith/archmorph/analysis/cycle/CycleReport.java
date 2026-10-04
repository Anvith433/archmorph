package com.anvith.archmorph.analysis.cycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stores all circular dependencies found in a project. */
public class CycleReport {

    private final List<DependencyCycle> structuredCycles = new ArrayList<>();

    public void addCycle(DependencyCycle cycle) {
        structuredCycles.add(cycle);
    }

    public List<DependencyCycle> getStructuredCycles() {
        return Collections.unmodifiableList(structuredCycles);
    }

    /** Representative cycle paths (backwards compatible). */
    public List<List<String>> getCycles() {
        return structuredCycles.stream().map(DependencyCycle::path).toList();
    }

    public int getCycleCount() {
        return structuredCycles.size();
    }

    public boolean hasCycles() {
        return !structuredCycles.isEmpty();
    }
}
