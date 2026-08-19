package com.anvith.archmorph.analysis.dependency;

import java.util.Objects;

/**
 * Represents a dependency between
 * two classes.
 *
 * Example:
 *
 * UserController
 *        |
 *        | FIELD
 *        ▼
 * UserService
 */
public class DependencyEdge {

    /*
     * Source Node
     */
    private DependencyNode source;

    /*
     * Target Node
     */
    private DependencyNode target;

    /*
     * Why the dependency exists
     */
    private DependencyType dependencyType;

    public DependencyEdge() {
    }

    public DependencyEdge(
            DependencyNode source,
            DependencyNode target,
            DependencyType dependencyType) {

        this.source = source;
        this.target = target;
        this.dependencyType = dependencyType;
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

    @Override
    public String toString() {

        return source.getClassName()
                + " --("
                + dependencyType
                + ")--> "
                + target.getClassName();
    }
@Override
public boolean equals(Object o) {

    if (this == o)
        return true;

    if (!(o instanceof DependencyEdge edge))
        return false;

    return Objects.equals(source, edge.source)
            &&
            Objects.equals(target, edge.target)
            &&
            dependencyType == edge.dependencyType;
}

    @Override
    public int hashCode() {

        return Objects.hash(
                source,
                target,
                dependencyType
        );
    }

}