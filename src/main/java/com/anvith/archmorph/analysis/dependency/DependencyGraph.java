package com.anvith.archmorph.analysis.dependency;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The dependency graph of an analysed project.
 *
 * <p>Nodes are keyed by fully-qualified name; edges are deduplicated by
 * (source, target, type) and merged. Outgoing and incoming adjacency maps make
 * traversal O(degree) instead of O(E) per step.</p>
 */
public class DependencyGraph {

    private final Map<String, DependencyNode> nodes = new LinkedHashMap<>();

    private final Map<DependencyEdge, DependencyEdge> edges = new LinkedHashMap<>();

    private final Map<DependencyNode, Set<DependencyEdge>> outgoing = new LinkedHashMap<>();

    private final Map<DependencyNode, Set<DependencyEdge>> incoming = new LinkedHashMap<>();

    /** Add a node. A node already present (same id) is kept. */
    public void addNode(DependencyNode node) {
        if (node == null || node.getClassName() == null || node.getClassName().isBlank()) {
            return;
        }
        nodes.putIfAbsent(node.getId(), node);
    }

    /** The canonical instance stored for this node's id, adding it if needed. */
    private DependencyNode canonical(DependencyNode node) {
        addNode(node);
        return nodes.get(node.getId());
    }

    /** Add an edge; duplicate edges are merged; self dependencies are ignored. */
    public void addEdge(DependencyEdge edge) {
        if (edge == null || edge.getSource() == null || edge.getTarget() == null) {
            return;
        }
        if (edge.getSource().equals(edge.getTarget())) {
            return;
        }
        edge.setSource(canonical(edge.getSource()));
        edge.setTarget(canonical(edge.getTarget()));

        DependencyEdge existing = edges.get(edge);
        if (existing != null) {
            existing.merge(edge);
            return;
        }
        edges.put(edge, edge);
        outgoing.computeIfAbsent(edge.getSource(), k -> new LinkedHashSet<>()).add(edge);
        incoming.computeIfAbsent(edge.getTarget(), k -> new LinkedHashSet<>()).add(edge);
    }

    public void addNodes(Collection<DependencyNode> nodes) {
        if (nodes != null) {
            nodes.forEach(this::addNode);
        }
    }

    public void addEdges(Collection<DependencyEdge> edges) {
        if (edges != null) {
            edges.forEach(this::addEdge);
        }
    }

    public Set<DependencyNode> getNodes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(nodes.values()));
    }

    public Set<DependencyEdge> getEdges() {
        return Collections.unmodifiableSet(edges.keySet());
    }

    /** Project (non-external) nodes only. */
    public List<DependencyNode> getInternalNodes() {
        return nodes.values().stream().filter(n -> !n.isExternal()).toList();
    }

    /** All outgoing dependencies of a class. */
    public Set<DependencyEdge> getOutgoingEdges(DependencyNode node) {
        if (node == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(outgoing.getOrDefault(node, Set.of()));
    }

    /** All incoming dependencies. */
    public Set<DependencyEdge> getIncomingEdges(DependencyNode node) {
        if (node == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(incoming.getOrDefault(node, Set.of()));
    }

    /** Distinct classes this node depends on. */
    public Set<DependencyNode> getSuccessors(DependencyNode node) {
        return getOutgoingEdges(node).stream().map(DependencyEdge::getTarget)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Distinct classes depending on this node. */
    public Set<DependencyNode> getPredecessors(DependencyNode node) {
        return getIncomingEdges(node).stream().map(DependencyEdge::getSource)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Sum of edge weights between a and b in both directions. */
    public double weightBetween(DependencyNode a, DependencyNode b) {
        double weight = 0;
        for (DependencyEdge edge : getOutgoingEdges(a)) {
            if (edge.getTarget().equals(b)) {
                weight += edge.getWeight();
            }
        }
        for (DependencyEdge edge : getOutgoingEdges(b)) {
            if (edge.getTarget().equals(a)) {
                weight += edge.getWeight();
            }
        }
        return weight;
    }

    /** Find a node by qualified name, or by simple name when unambiguous. */
    public DependencyNode findNode(String className) {
        if (className == null) {
            return null;
        }
        DependencyNode exact = nodes.get(className);
        if (exact != null) {
            return exact;
        }
        List<DependencyNode> bySimpleName = nodes.values().stream()
                .filter(n -> className.equals(n.getClassName()))
                .toList();
        return bySimpleName.size() == 1 ? bySimpleName.getFirst() : null;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getEdgeCount() {
        return edges.size();
    }

    public void clear() {
        nodes.clear();
        edges.clear();
        outgoing.clear();
        incoming.clear();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("DependencyGraph[nodes=")
                .append(nodes.size()).append(", edges=").append(edges.size()).append("]\n");
        edges.keySet().forEach(edge -> builder.append("  ").append(edge).append('\n'));
        return builder.toString();
    }
}
