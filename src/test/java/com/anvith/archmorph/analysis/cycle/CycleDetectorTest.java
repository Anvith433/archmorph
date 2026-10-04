package com.anvith.archmorph.analysis.cycle;

import com.anvith.archmorph.analysis.architecture.Severity;
import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.parser.ComponentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CycleDetectorTest {

    private final DefaultCircularDependencyDetector detector = new DefaultCircularDependencyDetector();

    private static DependencyNode node(String name, ComponentType type) {
        return new DependencyNode("com.x." + name, name, "com.x", type);
    }

    @Test
    void reportsEachStronglyConnectedComponentOnceWithAClosedPath() {
        DependencyGraph graph = new DependencyGraph();
        DependencyNode a = node("AService", ComponentType.SERVICE);
        DependencyNode b = node("BService", ComponentType.SERVICE);
        DependencyNode c = node("CService", ComponentType.SERVICE);
        graph.addEdge(new DependencyEdge(a, b, DependencyType.FIELD));
        graph.addEdge(new DependencyEdge(b, c, DependencyType.FIELD));
        graph.addEdge(new DependencyEdge(c, a, DependencyType.METHOD_INVOCATION));
        graph.addEdge(new DependencyEdge(b, a, DependencyType.FIELD));

        CycleReport report = detector.detect(graph);

        assertThat(report.getCycleCount()).isEqualTo(1);
        DependencyCycle cycle = report.getStructuredCycles().getFirst();
        assertThat(cycle.cycleId()).isEqualTo("cycle-001");
        assertThat(cycle.nodes()).containsExactly("com.x.AService", "com.x.BService", "com.x.CService");
        assertThat(cycle.path().getFirst()).isEqualTo(cycle.path().getLast());
        assertThat(cycle.edgeCount()).isEqualTo(4);
        assertThat(cycle.dependencyTypes()).contains(DependencyType.FIELD, DependencyType.METHOD_INVOCATION);
        assertThat(cycle.recommendation()).isNotBlank();
    }

    @Test
    void severityReflectsTheKindOfCycle() {
        DependencyGraph graph = new DependencyGraph();
        DependencyNode user = node("User", ComponentType.ENTITY);
        DependencyNode order = node("Order", ComponentType.ENTITY);
        graph.addEdge(new DependencyEdge(user, order, DependencyType.ENTITY_RELATIONSHIP));
        graph.addEdge(new DependencyEdge(order, user, DependencyType.ENTITY_RELATIONSHIP));

        DependencyNode service = node("UserService", ComponentType.SERVICE);
        DependencyNode repository = node("UserRepository", ComponentType.REPOSITORY);
        graph.addEdge(new DependencyEdge(service, repository, DependencyType.FIELD));
        graph.addEdge(new DependencyEdge(repository, service, DependencyType.FIELD));

        CycleReport report = detector.detect(graph);

        assertThat(report.getStructuredCycles()).extracting(DependencyCycle::severity)
                .containsExactlyInAnyOrder(Severity.LOW, Severity.HIGH);
    }

    @Test
    void acyclicGraphHasNoCycles() {
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(new DependencyEdge(node("A", ComponentType.SERVICE), node("B", ComponentType.SERVICE), DependencyType.FIELD));
        assertThat(detector.detect(graph).hasCycles()).isFalse();
    }
}
