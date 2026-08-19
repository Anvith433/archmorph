package com.anvith.archmorph.analysis.dependency;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents the complete dependency graph
 * of the uploaded project.
 *
 * Contains:
 *  - Nodes (Classes)
 *  - Edges (Dependencies)
 */
public class DependencyGraph {

    /*
     * All unique classes
     */
    private final Set<DependencyNode> nodes =
            new LinkedHashSet<>();

    /*
     * All unique dependencies
     */
    private final Set<DependencyEdge> edges =
            new LinkedHashSet<>();

    /**
     * Add a node.
     */
   public void addNode(DependencyNode node) {

    if (node == null) {
        return;
    }

    if (node.getClassName() == null ||
            node.getClassName().isBlank()) {
        return;
    }

    nodes.add(node);
}
    /**
     * Add an edge.
     */
   public void addEdge(DependencyEdge edge) {

    if (edge == null) {
        return;
    }

    /*
     * Ignore self dependency
     */
    if (edge.getSource().equals(edge.getTarget())) {
        return;
    }

    edges.add(edge);
}

    /**
     * Add multiple nodes.
     */
    public void addNodes(Set<DependencyNode> nodes) {

        if (nodes != null) {
            this.nodes.addAll(nodes);
        }

    }

    /**
     * Add multiple edges.
     */
    public void addEdges(Set<DependencyEdge> edges) {

        if (edges != null) {
            this.edges.addAll(edges);
        }

    }

    /**
     * All graph nodes.
     */
    public Set<DependencyNode> getNodes() {

        return Collections.unmodifiableSet(nodes);

    }

    /**
     * All graph edges.
     */
    public Set<DependencyEdge> getEdges() {

        return Collections.unmodifiableSet(edges);

    }


    /**
 * All outgoing dependencies of a class.
 */
public Set<DependencyEdge> getOutgoingEdges(
        DependencyNode node) {

    Set<DependencyEdge> result =
            new LinkedHashSet<>();

    if (node == null) {
        return result;
    }

    for (DependencyEdge edge : edges) {

        if (edge.getSource().equals(node)) {
            result.add(edge);
        }

    }

    return result;
}


    /**
 * Find a node by class name.
 */
public DependencyNode findNode(String className) {

    if (className == null) {
        return null;
    }

    for (DependencyNode node : nodes) {

        if (className.equals(node.getClassName())) {
            return node;
        }

    }

    return null;
}

    /**
     * Number of nodes.
     */
    public int getNodeCount() {

        return nodes.size();

    }

    /**
     * Number of edges.
     */
    public int getEdgeCount() {

        return edges.size();

    }

    /**
     * Remove everything.
     */
    public void clear() {

        nodes.clear();

        edges.clear();

    }

    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder();

        builder.append("\n");
        builder.append("========== DEPENDENCY GRAPH ==========\n");

        builder.append("Nodes : ")
                .append(nodes.size())
                .append("\n");

        builder.append("Edges : ")
                .append(edges.size())
                .append("\n\n");

        for (DependencyEdge edge : edges) {

            builder.append(edge)
                    .append("\n");

        }

        builder.append("======================================");

        return builder.toString();
    }

    /**
 * All incoming dependencies.
 */
public Set<DependencyEdge> getIncomingEdges(
        DependencyNode node) {

    Set<DependencyEdge> result =
            new LinkedHashSet<>();

    if (node == null) {
        return result;
    }

    for (DependencyEdge edge : edges) {

        if (edge.getTarget().equals(node)) {
            result.add(edge);
        }

    }

    return result;
}

}