package com.anvith.archmorph.analysis.architecture;

import com.anvith.archmorph.analysis.architecture.rules.LayerRuleRegistry;
import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.analysis.dependency.SourceLocation;
import com.anvith.archmorph.parser.ComponentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureAnalyzerTest {

    private final DefaultArchitectureAnalyzer analyzer =
            new DefaultArchitectureAnalyzer(new LayerAnalyzer(), new LayerViolationDetector(new LayerRuleRegistry()));

    @Test
    void countsLayersComputesCouplingAndFindsViolations() {
        DependencyNode controller = new DependencyNode("com.x.controller.UserController", "UserController", "com.x.controller", ComponentType.CONTROLLER);
        DependencyNode service = new DependencyNode("com.x.service.UserService", "UserService", "com.x.service", ComponentType.SERVICE);
        DependencyNode repository = new DependencyNode("com.x.repository.UserRepository", "UserRepository", "com.x.repository", ComponentType.REPOSITORY);
        DependencyNode unknown = new DependencyNode("com.x.util.Thing", "Thing", "com.x.util", ComponentType.UNKNOWN);
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(new DependencyEdge(controller, service, DependencyType.FIELD));
        graph.addEdge(new DependencyEdge(service, repository, DependencyType.FIELD));
        graph.addEdge(new DependencyEdge(controller, repository, DependencyType.FIELD, 1.0, new SourceLocation("UserController.java", 12)));
        graph.addNode(unknown);

        ArchitectureReport report = analyzer.analyze(graph);

        assertThat(report.getClassCount()).isEqualTo(4);
        assertThat(report.getControllerCount()).isEqualTo(1);
        assertThat(report.getUnknownCount()).isEqualTo(1);
        assertThat(report.getDependencyCount()).isEqualTo(3);
        assertThat(report.getPackageStyle()).isEqualTo(ArchitectureReport.PackageStyle.LAYERED);

        assertThat(report.getLayerViolations()).singleElement().satisfies(v -> {
            assertThat(v.sourceType()).isEqualTo(ComponentType.CONTROLLER);
            assertThat(v.targetType()).isEqualTo(ComponentType.REPOSITORY);
            assertThat(v.location().line()).isEqualTo(12);
            assertThat(v.message()).contains("must not depend on");
        });

        ClassMetric serviceMetric = report.getClassMetrics().stream()
                .filter(m -> m.className().equals("UserService")).findFirst().orElseThrow();
        assertThat(serviceMetric.afferentCoupling()).isEqualTo(1);
        assertThat(serviceMetric.efferentCoupling()).isEqualTo(1);
        assertThat(serviceMetric.instability()).isEqualTo(0.5);
        ClassMetric repositoryMetric = report.getClassMetrics().stream()
                .filter(m -> m.className().equals("UserRepository")).findFirst().orElseThrow();
        assertThat(repositoryMetric.instability()).isZero();
        assertThat(report.getArchitectureHealthIndicator()).isBetween(0, 99);
    }
}
