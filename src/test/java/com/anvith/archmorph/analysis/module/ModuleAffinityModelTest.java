package com.anvith.archmorph.analysis.module;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.analysis.module.affinity.AffinityBreakdown;
import com.anvith.archmorph.analysis.module.affinity.ModuleAffinityModel;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.parser.ComponentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleAffinityModelTest {

    private static DependencyNode node(String pkg, String name, ComponentType type) {
        return new DependencyNode(pkg + "." + name, name, pkg, type);
    }

    @Test
    void sameDomainScoresHigherThanCrossDomain() {
        DependencyNode userService = node("com.x.service", "UserService", ComponentType.SERVICE);
        DependencyNode userRepository = node("com.x.repository", "UserRepository", ComponentType.REPOSITORY);
        DependencyNode orderService = node("com.x.service", "OrderService", ComponentType.SERVICE);
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(new DependencyEdge(userService, userRepository, DependencyType.FIELD));
        graph.addEdge(new DependencyEdge(orderService, userService, DependencyType.FIELD));

        ModuleAffinityModel model = new ModuleAffinityModel(graph, Map.of(), new ArchMorphProperties.ModuleDiscovery(), "com.x");
        AffinityBreakdown same = model.affinity(userService, userRepository);
        AffinityBreakdown cross = model.affinity(orderService, userService);

        assertThat(same.total()).isGreaterThan(cross.total());
        assertThat(same.naming()).isEqualTo(1.0);
        assertThat(cross.crossDomainPenalty()).isEqualTo(1.0);
        assertThat(same.pkg()).as("layer packages carry no domain signal").isZero();
        assertThat(same.explain()).contains("naming");
    }

    @Test
    void weightsAreNormalisedAndScoresBounded() {
        ArchMorphProperties.ModuleDiscovery config = new ArchMorphProperties.ModuleDiscovery();
        config.setDependencyWeight(30);
        config.setNamingWeight(20);
        DependencyNode a = node("com.x.user", "UserService", ComponentType.SERVICE);
        DependencyNode b = node("com.x.user", "User", ComponentType.ENTITY);
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge(new DependencyEdge(a, b, DependencyType.FIELD));
        graph.addEdge(new DependencyEdge(a, b, DependencyType.METHOD_RETURN));

        AffinityBreakdown result = new ModuleAffinityModel(graph, Map.of(), config, "com.x").affinity(a, b);
        assertThat(result.total()).isBetween(0.0, 1.0);
        assertThat(result.pkg()).isEqualTo(1.0);
    }
}
