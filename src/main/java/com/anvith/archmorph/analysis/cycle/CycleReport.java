package com.anvith.archmorph.analysis.cycle;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores all circular dependencies
 * found in a project.
 */
public class CycleReport {

    /*
     * Every detected cycle.
     */
    private final List<List<String>> cycles =
            new ArrayList<>();

    /**
     * Add one cycle.
     */
    public void addCycle(List<String> cycle) {

        cycles.add(cycle);

    }

    /**
     * All cycles.
     */
    public List<List<String>> getCycles() {

        return cycles;

    }

    /**
     * Number of cycles.
     */
    public int getCycleCount() {

        return cycles.size();

    }

    /**
     * Any cycle found?
     */
    public boolean hasCycles() {

        return !cycles.isEmpty();

    }

}