package com.anvith.archmorph.analysis.dependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a dependency between two classes.
 *
 * <pre>
 * UserController --(FIELD, 1 occurrence, UserController.java:14)--> UserService
 * </pre>
 *
 * <p>Identity is (source, target, type). Repeated occurrences are merged
 * into one edge whose {@link #getOccurrences()} counts them; up to
 * {@value #MAX_LOCATIONS} locations are retained.</p>
 */
public class DependencyEdge {

    public static final int MAX_LOCATIONS = 5;

    private DependencyNode source;

    private DependencyNode target;

    /** Why the dependency exists. */
    private DependencyType dependencyType;

    /** How sure the resolver is that the target is the right class (0..1). */
    private double confidence = 1.0;

    private int occurrences = 1;

    private final List<SourceLocation> locations = new ArrayList<>();

    public DependencyEdge() {
    }

    public DependencyEdge(DependencyNode source, DependencyNode target, DependencyType dependencyType) {
        this.source = source;
        this.target = target;
        this.dependencyType = dependencyType;
    }

    public DependencyEdge(DependencyNode source, DependencyNode target, DependencyType dependencyType,
                          double confidence, SourceLocation location) {
        this(source, target, dependencyType);
        this.confidence = confidence;
        if (location != null) {
            locations.add(location);
        }
    }

    /** Merge another occurrence of the same dependency into this edge. */
    void merge(DependencyEdge other) {
        occurrences += other.occurrences;
        confidence = Math.max(confidence, other.confidence);
        for (SourceLocation location : other.locations) {
            if (locations.size() >= MAX_LOCATIONS) {
                break;
            }
            if (!locations.contains(location)) {
                locations.add(location);
            }
        }
    }

    /** Affinity weight: type weight scaled by confidence and (logarithmically) by occurrences. */
    public double getWeight() {
        return dependencyType.getWeight() * confidence * (1.0 + Math.log(occurrences) / 3.0);
    }

    public DependencyNode getSource() {
        return source;
    }

    public void setSource(DependencyNode source) {
        this.source = source;
    }

    public DependencyNode getTarget() {
        return target;
    }

    public void setTarget(DependencyNode target) {
        this.target = target;
    }

    public DependencyType getDependencyType() {
        return dependencyType;
    }

    public void setDependencyType(DependencyType dependencyType) {
        this.dependencyType = dependencyType;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public List<SourceLocation> getLocations() {
        return Collections.unmodifiableList(locations);
    }

    /** First recorded location, or null. */
    public SourceLocation getLocation() {
        return locations.isEmpty() ? null : locations.getFirst();
    }

    @Override
    public String toString() {
        return source.getClassName() + " --(" + dependencyType + ")--> " + target.getClassName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DependencyEdge edge)) {
            return false;
        }
        return Objects.equals(source, edge.source)
                && Objects.equals(target, edge.target)
                && dependencyType == edge.dependencyType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, dependencyType);
    }
}
