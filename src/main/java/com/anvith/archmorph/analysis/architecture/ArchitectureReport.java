package com.anvith.archmorph.analysis.architecture;

import java.util.ArrayList;
import java.util.List;

public class ArchitectureReport {

    /*
     * Component Counts
     */
    private int controllerCount;

    private int serviceCount;

    private int repositoryCount;

    private int entityCount;

    private int componentCount;

    private int configurationCount;

    /*
     * Dependency Information
     */
    private int dependencyCount;

    /*
     * Architecture Issues
     */
    private final List<String> violations =
            new ArrayList<>();

    private final List<String> warnings =
            new ArrayList<>();

    private final List<String> recommendations =
            new ArrayList<>();

    /*
     * Getters & Setters
     */

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

    public int getDependencyCount() {
        return dependencyCount;
    }

    public void setDependencyCount(int dependencyCount) {
        this.dependencyCount = dependencyCount;
    }

    public List<String> getViolations() {
        return violations;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

}