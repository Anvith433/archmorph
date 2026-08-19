package com.anvith.archmorph.analysis.transformation.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TransformationMappingReport {

    /*
     * Complete transformation plan.
     */
    private final List<TransformationMapping> mappings =
            new ArrayList<>();

    /**
     * Add one mapping.
     */
    public void addMapping(
            TransformationMapping mapping) {

        if (mapping != null) {
            mappings.add(mapping);
        }

    }

    /**
     * All mappings.
     */
    public List<TransformationMapping> getMappings() {

        return Collections.unmodifiableList(mappings);

    }

    /**
     * Number of mappings.
     */
    public int size() {

        return mappings.size();

    }

    /**
     * Is report empty?
     */
    public boolean isEmpty() {

        return mappings.isEmpty();

    }

    @Override
    public String toString() {

        StringBuilder builder =
                new StringBuilder();

        builder.append("\n");
        builder.append("========== TRANSFORMATION PLAN ==========\n");

        if (mappings.isEmpty()) {

            builder.append("No transformation mappings generated.\n");

        } else {

            for (TransformationMapping mapping : mappings) {

                builder.append(mapping)
                        .append("\n\n");

            }

        }

        builder.append("=========================================");

        return builder.toString();

    }

    

}