package com.anvith.archmorph.analysis.architecture;

import com.anvith.archmorph.parser.ComponentType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Architecture analysis result. All metrics are static-analysis indicators,
 * not absolute measures of quality.
 */
public class ArchitectureReport {

    public enum PackageStyle {
        /** Packages named after layers (controller, service, ...). */
        LAYERED,
        /** Packages named after features/domains. */
        FEATURE_BASED,
        MIXED,
        FLAT
    }

    /*
     * Component Counts
     */
    private int classCount;
    private int interfaceCount;
    private int controllerCount;
    private int serviceCount;
    private int repositoryCount;
    private int entityCount;
    private int dtoCount;
    private int componentCount;
    private int configurationCount;
    private int securityCount;
    private int exceptionCount;
    private int unknownCount;
    private final Map<ComponentType, Integer> componentDistribution = new EnumMap<>(ComponentType.class);

    /*
     * Dependency Information
     */
    private int dependencyCount;
    private int cycleCount;
    private double averageInstability;
    private PackageStyle packageStyle = PackageStyle.FLAT;

    /** 0..100 indicator combining violations, cycles and unclassified classes. */
    private int architectureHealthIndicator;

    /*
     * Architecture Issues
     */
    private final List<LayerViolation> layerViolations = new ArrayList<>();
    private final List<ClassMetric> classMetrics = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> recommendations = new ArrayList<>();

    /** Human-readable violation messages (backwards compatible). */
    public List<String> getViolations() {
        return layerViolations.stream().map(LayerViolation::message).toList();
    }

    public int getViolationCount() {
        return layerViolations.size();
    }

    public void increment(ComponentType type) {
        componentDistribution.merge(type, 1, Integer::sum);
    }

    public int getClassCount() {
        return classCount;
    }

    public void setClassCount(int classCount) {
        this.classCount = classCount;
    }

    public int getInterfaceCount() {
        return interfaceCount;
    }

    public void setInterfaceCount(int interfaceCount) {
        this.interfaceCount = interfaceCount;
    }

    public int getControllerCount() {
        return controllerCount;
    }

    public void setControllerCount(int controllerCount) {
        this.controllerCount = controllerCount;
    }

    public int getServiceCount() {
        return serviceCount;
    }

    public void setServiceCount(int serviceCount) {
        this.serviceCount = serviceCount;
    }

    public int getRepositoryCount() {
        return repositoryCount;
    }

    public void setRepositoryCount(int repositoryCount) {
        this.repositoryCount = repositoryCount;
    }

    public int getEntityCount() {
        return entityCount;
    }

    public void setEntityCount(int entityCount) {
        this.entityCount = entityCount;
    }

    public int getDtoCount() {
        return dtoCount;
    }

    public void setDtoCount(int dtoCount) {
        this.dtoCount = dtoCount;
    }

    public int getComponentCount() {
        return componentCount;
    }

    public void setComponentCount(int componentCount) {
        this.componentCount = componentCount;
    }

    public int getConfigurationCount() {
        return configurationCount;
    }

    public void setConfigurationCount(int configurationCount) {
        this.configurationCount = configurationCount;
    }

    public int getSecurityCount() {
        return securityCount;
    }

    public void setSecurityCount(int securityCount) {
        this.securityCount = securityCount;
    }

    public int getExceptionCount() {
        return exceptionCount;
    }

    public void setExceptionCount(int exceptionCount) {
        this.exceptionCount = exceptionCount;
    }

    public int getUnknownCount() {
        return unknownCount;
    }

    public void setUnknownCount(int unknownCount) {
        this.unknownCount = unknownCount;
    }

    public Map<ComponentType, Integer> getComponentDistribution() {
        return componentDistribution;
    }

    public int getDependencyCount() {
        return dependencyCount;
    }

    public void setDependencyCount(int dependencyCount) {
        this.dependencyCount = dependencyCount;
    }

    public int getCycleCount() {
        return cycleCount;
    }

    public void setCycleCount(int cycleCount) {
        this.cycleCount = cycleCount;
    }

    public double getAverageInstability() {
        return averageInstability;
    }

    public void setAverageInstability(double averageInstability) {
        this.averageInstability = averageInstability;
    }

    public PackageStyle getPackageStyle() {
        return packageStyle;
    }

    public void setPackageStyle(PackageStyle packageStyle) {
        this.packageStyle = packageStyle;
    }

    public int getArchitectureHealthIndicator() {
        return architectureHealthIndicator;
    }

    public void setArchitectureHealthIndicator(int architectureHealthIndicator) {
        this.architectureHealthIndicator = architectureHealthIndicator;
    }

    public List<LayerViolation> getLayerViolations() {
        return layerViolations;
    }

    public List<ClassMetric> getClassMetrics() {
        return classMetrics;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }
}
