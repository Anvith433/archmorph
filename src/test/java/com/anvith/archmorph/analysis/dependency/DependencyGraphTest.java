package com.anvith.archmorph.analysis.dependency;

import com.anvith.archmorph.parser.ComponentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyGraphTest {

    private final DependencyNode controller = new DependencyNode("com.a.UserController", "UserController", "com.a", ComponentType.CONTROLLER);
    private final DependencyNode service = new DependencyNode("com.a.UserService", "UserService", "com.a", ComponentType.SERVICE);

    @Test
    void mergesDuplicateEdgesAndKeepsLocations() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(new DependencyEdge(controller, service, DependencyType.FIELD, 0.9, new SourceLocation("A.java", 3)));
        graph.addEdge(new DependencyEdge(controller, service, DependencyType.FIELD, 1.0, new SourceLocation("A.java", 9)));
        graph.addEdge(new DependencyEdge(controller, service, DependencyType.METHOD_INVOCATION));

        assertThat(graph.getEdgeCount()).isEqualTo(2);
        assertThat(graph.getNodeCount()).isEqualTo(2);
        DependencyEdge field = graph.getOutgoingEdges(controller).stream()
                .filter(e -> e.getDependencyType() == DependencyType.FIELD).findFirst().orElseThrow();
        assertThat(field.getOccurrences()).isEqualTo(2);
        assertThat(field.getConfidence()).isEqualTo(1.0);
        assertThat(field.getLocations()).extracting(SourceLocation::line).containsExactly(3, 9);
    }

    @Test
    void adjacencyMapsSupportBothDirections() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(new DependencyEdge(controller, service, DependencyType.FIELD));

        assertThat(graph.getSuccessors(controller)).containsExactly(service);
        assertThat(graph.getPredecessors(service)).containsExactly(controller);
        assertThat(graph.getIncomingEdges(controller)).isEmpty();
        assertThat(graph.weightBetween(controller, service)).isEqualTo(graph.weightBetween(service, controller));
    }

    @Test
    void ignoresSelfDependenciesAndDistinguishesSameSimpleNames() {
        DependencyGraph graph = new DependencyGraph();
        DependencyNode otherService = new DependencyNode("com.b.UserService", "UserService", "com.b", ComponentType.SERVICE);
        graph.addEdge(new DependencyEdge(service, service, DependencyType.FIELD));
        graph.addNode(service);
        graph.addNode(otherService);

        assertThat(graph.getEdgeCount()).isZero();
        assertThat(graph.getNodeCount()).isEqualTo(2);
        assertThat(graph.findNode("UserService")).isNull();
        assertThat(graph.findNode("com.b.UserService")).isSameAs(otherService);
    }
}
