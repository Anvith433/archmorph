package com.anvith.archmorph.analysis.transformation.planner;

import java.util.ArrayList;
import java.util.List;

public class TransformationPlan {

    private final List<TransformationPlanEntry> entries =
            new ArrayList<>();

    public void addEntry(
            TransformationPlanEntry entry) {

        entries.add(entry);

    }

    public List<TransformationPlanEntry> getEntries() {
        return entries;
    }

}