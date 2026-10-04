package com.anvith.archmorph.analysis.module;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.analysis.module.editing.ModuleEdit;
import com.anvith.archmorph.analysis.module.editing.ModuleEditService;
import com.anvith.archmorph.common.exception.ArchMorphException;
import com.anvith.archmorph.parser.ComponentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleEditServiceTest {

    private final ModuleEditService service = new ModuleEditService(new ModuleMetricsCalculator());
    private DependencyGraph graph;
    private ModuleDiscoveryReport suggestion;

    private static DependencyNode node(String name, ComponentType type) {
        return new DependencyNode("com.x." + name, name, "com.x", type);
    }

    @BeforeEach
    void setUp() {
        graph = new DependencyGraph();
        DependencyNode user = node("User", ComponentType.ENTITY);
        DependencyNode userService = node("UserService", ComponentType.SERVICE);
        DependencyNode order = node("Order", ComponentType.ENTITY);
        DependencyNode orderService = node("OrderService", ComponentType.SERVICE);
        DependencyNode api = node("ApiResponse", ComponentType.DTO);
        graph.addEdge(new DependencyEdge(userService, user, DependencyType.FIELD));
        graph.addEdge(new DependencyEdge(orderService, order, DependencyType.FIELD));
        graph.addEdge(new DependencyEdge(orderService, userService, DependencyType.FIELD));
        graph.addNode(api);

        suggestion = new ModuleDiscoveryReport();
        for (DependencyNode n : List.of(user, userService)) {
            suggestion.assign(n, "user", ModuleCategory.BUSINESS_MODULE, 0.9, ClassAssignment.Origin.AUTOMATIC, List.of("t"));
        }
        for (DependencyNode n : List.of(order, orderService)) {
            suggestion.assign(n, "order", ModuleCategory.BUSINESS_MODULE, 0.9, ClassAssignment.Origin.AUTOMATIC, List.of("t"));
        }
        suggestion.assign(api, "shared", ModuleCategory.SHARED, 0.9, ClassAssignment.Origin.AUTOMATIC, List.of("t"));
    }

    private static ModuleEdit edit(ModuleEdit.Type type, String module, String newName, String target, List<String> sources,
                                   String className, List<String> classes) {
        return new ModuleEdit(type, module, newName, target, sources, className, classes);
    }

    @Test
    void renameMergeSplitAndMoveProduceAFinalPlanWithoutTouchingTheSuggestion() {
        ModuleDiscoveryReport result = service.apply(suggestion, List.of(
                edit(ModuleEdit.Type.RENAME_MODULE, "user", "customer", null, null, null, null),
                edit(ModuleEdit.Type.MERGE_MODULES, null, null, "customer", List.of("order"), null, null),
                edit(ModuleEdit.Type.SPLIT_MODULE, "customer", "ordering", null, null, null, List.of("com.x.Order", "com.x.OrderService")),
                edit(ModuleEdit.Type.MOVE_CLASS, null, null, "ordering", null, "com.x.User", null),
                edit(ModuleEdit.Type.MOVE_TO_SHARED, null, null, null, null, "com.x.UserService", null)), graph);

        assertThat(result.getBusinessModules()).extracting(ModuleInfo::getModuleName).containsExactly("ordering");
        assertThat(result.moduleOf("com.x.User")).isEqualTo("ordering");
        assertThat(result.moduleOf("com.x.UserService")).isEqualTo("shared");
        assertThat(result.getAssignment("com.x.User").origin()).isEqualTo(ClassAssignment.Origin.USER);
        assertThat(suggestion.moduleOf("com.x.User")).as("suggestion unchanged").isEqualTo("user");

        long placements = result.getModules().stream().mapToLong(ModuleInfo::getClassCount).sum();
        assertThat(placements).as("every class exactly once").isEqualTo(5);
    }

    @Test
    void lockedClassesCannotBeMoved() {
        assertThatThrownBy(() -> service.apply(suggestion, List.of(
                edit(ModuleEdit.Type.LOCK_CLASS, null, null, null, null, "com.x.User", null),
                edit(ModuleEdit.Type.MOVE_CLASS, null, null, "order", null, "com.x.User", null)), graph))
                .isInstanceOf(ArchMorphException.class).hasMessageContaining("locked");
    }

    @Test
    void excludeIsRecordedAndReversible() {
        ModuleDiscoveryReport excluded = service.apply(suggestion,
                List.of(edit(ModuleEdit.Type.EXCLUDE_CLASS, null, null, null, null, "com.x.Order", null)), graph);
        assertThat(excluded.getAssignment("com.x.Order").excluded()).isTrue();
        ModuleDiscoveryReport included = service.apply(suggestion, List.of(
                edit(ModuleEdit.Type.EXCLUDE_CLASS, null, null, null, null, "com.x.Order", null),
                edit(ModuleEdit.Type.INCLUDE_CLASS, null, null, null, null, "com.x.Order", null)), graph);
        assertThat(included.getAssignment("com.x.Order").excluded()).isFalse();
    }

    @Test
    void rejectsInvalidOperations() {
        assertThatThrownBy(() -> service.apply(suggestion,
                List.of(edit(ModuleEdit.Type.RENAME_MODULE, "user", "Bad Name!", null, null, null, null)), graph))
                .isInstanceOf(ArchMorphException.class);
        assertThatThrownBy(() -> service.apply(suggestion,
                List.of(edit(ModuleEdit.Type.RENAME_MODULE, "user", "shared", null, null, null, null)), graph))
                .isInstanceOf(ArchMorphException.class);
        assertThatThrownBy(() -> service.apply(suggestion,
                List.of(edit(ModuleEdit.Type.RENAME_MODULE, "user", "order", null, null, null, null)), graph))
                .hasMessageContaining("already exists");
        assertThatThrownBy(() -> service.apply(suggestion,
                List.of(edit(ModuleEdit.Type.MOVE_CLASS, null, null, "order", null, "com.x.<script>", null)), graph))
                .hasMessageNotContaining("<script>");
        assertThatThrownBy(() -> service.apply(suggestion,
                List.of(edit(ModuleEdit.Type.SPLIT_MODULE, "user", "rest", null, null, null, List.of("com.x.User", "com.x.UserService"))), graph))
                .hasMessageContaining("at least one class");
    }

    @Test
    void metricsAreRecalculatedAfterEdits() {
        ModuleDiscoveryReport result = service.apply(suggestion, List.of(), graph);
        ModuleInfo user = result.getModule("user");
        assertThat(user.getInternalDependencies()).isEqualTo(1);
        assertThat(user.getCohesion()).isEqualTo(0.5);
        assertThat(user.getConfidence()).isBetween(0.0, 1.0);
    }
}
