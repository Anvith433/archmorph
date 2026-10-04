package com.anvith.archmorph.analysis.registry;

import com.anvith.archmorph.parser.ClassMetadata;

/** Registers discovered classes into one analysis' registry. */
public class ProjectClassCollector {

    private final ProjectClassRegistry registry;

    public ProjectClassCollector(ProjectClassRegistry registry) {
        this.registry = registry;
    }

    /** Register one discovered class. */
    public void collect(ClassMetadata metadata) {
        registry.register(ProjectClassInfo.of(metadata));
    }
}
