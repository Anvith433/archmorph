package com.anvith.archmorph.analysis.transformation.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** All class mappings of a project plus the complete {@code old FQN -> new FQN} map. */
public class TransformationMappingReport {

    private final List<TransformationMapping> mappings = new ArrayList<>();

    private String basePackage = "";

    public void addMapping(TransformationMapping mapping) {
        if (mapping != null) {
            mappings.add(mapping);
        }
    }

    public List<TransformationMapping> getMappings() {
        return Collections.unmodifiableList(mappings);
    }

    public Optional<TransformationMapping> find(String sourceQualifiedName) {
        return mappings.stream().filter(m -> m.getSourceQualifiedName().equals(sourceQualifiedName)).findFirst();
    }

    /** Complete map of source qualified name to target qualified name for top-level classes. */
    public Map<String, String> classMap() {
        Map<String, String> map = new LinkedHashMap<>();
        mappings.forEach(m -> map.put(m.getSourceQualifiedName(), m.getTargetQualifiedName()));
        return map;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    public int size() {
        return mappings.size();
    }

    public boolean isEmpty() {
        return mappings.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("TransformationMappingReport[").append(mappings.size()).append("]\n");
        mappings.forEach(m -> builder.append("  ").append(m).append('\n'));
        return builder.toString();
    }
}
