package com.anvith.archmorph.analysis.module;

import com.anvith.archmorph.analysis.dependency.DependencyNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A candidate module with its quality indicators. All numbers are
 * static-analysis indicators derived from the dependency graph.
 */
public class ModuleInfo {

    private String moduleName;

    private ModuleCategory category = ModuleCategory.BUSINESS_MODULE;

    /** Classes belonging to this module (top-level types). */
    private final Set<DependencyNode> classes = new LinkedHashSet<>();

    /** 0..1 confidence that this is a coherent business module. */
    private double confidence;

    /** internal / (internal + crossing) dependencies, 0..1. */
    private double cohesion;

    /** Share of this module's dependencies that cross into other business modules, 0..1. */
    private double externalCoupling;

    /** Dependencies between classes of this module. */
    private int internalDependencies;

    /** Dependencies leaving this module (to other modules or shared code). */
    private int externalDependencies;

    /** Outgoing dependency count per target module (including "shared"). */
    private final Map<String, Integer> dependenciesOnModules = new LinkedHashMap<>();

    private final List<String> evidence = new ArrayList<>();

    private final List<String> warnings = new ArrayList<>();

    public ModuleInfo() {
    }

    public ModuleInfo(String moduleName) {
        this.moduleName = moduleName;
    }

    public ModuleInfo(String moduleName, ModuleCategory category) {
        this.moduleName = moduleName;
        this.category = category;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public ModuleCategory getCategory() {
        return category;
    }

    public void setCategory(ModuleCategory category) {
        this.category = category;
    }

    public Set<DependencyNode> getClasses() {
        return classes;
    }

    public void addClass(DependencyNode node) {
        if (node != null) {
            classes.add(node);
        }
    }

    public boolean contains(DependencyNode node) {
        return classes.contains(node);
    }

    public int getClassCount() {
        return classes.size();
    }

    public boolean isEmpty() {
        return classes.isEmpty();
    }

    public boolean isBusinessModule() {
        return category == ModuleCategory.BUSINESS_MODULE;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public double getCohesion() {
        return cohesion;
    }

    public void setCohesion(double cohesion) {
        this.cohesion = cohesion;
    }

    public double getExternalCoupling() {
        return externalCoupling;
    }

    public void setExternalCoupling(double externalCoupling) {
        this.externalCoupling = externalCoupling;
    }

    public int getInternalDependencies() {
        return internalDependencies;
    }

    public void setInternalDependencies(int internalDependencies) {
        this.internalDependencies = internalDependencies;
    }

    public int getExternalDependencies() {
        return externalDependencies;
    }

    public void setExternalDependencies(int externalDependencies) {
        this.externalDependencies = externalDependencies;
    }

    public Map<String, Integer> getDependenciesOnModules() {
        return dependenciesOnModules;
    }

    public List<String> getEvidence() {
        return evidence;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(moduleName).append(" [").append(category).append("]\n");
        classes.forEach(node -> builder.append("   - ").append(node.getClassName()).append('\n'));
        return builder.toString();
    }
}
