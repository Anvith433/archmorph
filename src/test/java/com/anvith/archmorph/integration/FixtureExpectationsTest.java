package com.anvith.archmorph.integration;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.module.ModuleInfo;
import com.anvith.archmorph.analysis.transformation.SafetyLevel;
import com.anvith.archmorph.analysis.transformation.planner.PlanConflict;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanEntry;
import com.anvith.archmorph.analysis.validation.LevelResult;
import com.anvith.archmorph.analysis.validation.ValidationLevel;
import com.anvith.archmorph.analysis.validation.ValidationStatus;
import com.anvith.archmorph.support.ArchMorphTest;
import com.anvith.archmorph.support.Fixtures;
import com.anvith.archmorph.support.InMemoryCompiler;
import com.anvith.archmorph.support.PipelineRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the whole pipeline (analyse → discover → plan → transform → re-parse → validate) on every
 * fixture and checks the behaviour declared in its {@code expected.json}.
 */
@ArchMorphTest
class FixtureExpectationsTest {

    @Autowired
    PipelineRunner runner;

    @TempDir
    Path temp;

    static List<String> fixtures() {
        return Fixtures.names();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void fixtureBehavesAsExpected(String fixture) throws Exception {
        JsonNode expected = JsonMapper.builder().build().readTree(Fixtures.path(fixture).resolve("expected.json").toFile());
        // expectations are written for MODULAR_BY_DOMAIN unless the fixture names another strategy
        var strategy = com.anvith.archmorph.analysis.transformation.target.TargetStrategy.valueOf(
                expected.path("strategy").asString("MODULAR_BY_DOMAIN"));
        PipelineRunner.Run run = runner.run(fixture, temp, strategy);

        // ---- modules
        Set<String> businessModules = run.analysis().suggestion().getBusinessModules().stream()
                .map(ModuleInfo::getModuleName).collect(Collectors.toCollection(TreeSet::new));
        if (expected.has("modules") && !expected.get("modules").isNull()) {
            assertThat(businessModules).as("business modules").containsExactlyInAnyOrderElementsOf(strings(expected.get("modules")));
        }
        if (expected.path("ambiguous").asBoolean(false)) {
            assertThat(businessModules).as("no invented modules").isEmpty();
            boolean warned = !run.analysis().suggestion().getWarnings().isEmpty()
                    || run.analysis().suggestion().getModules().stream().anyMatch(m -> !m.getWarnings().isEmpty());
            assertThat(warned).as("ambiguity is reported").isTrue();
        }
        for (String shared : strings(expected.path("sharedClasses"))) {
            assertThat(run.analysis().suggestion().moduleOf(shared)).as("shared placement of " + shared).isEqualTo("shared");
        }

        // ---- plan
        for (var move : expected.path("moved").properties()) {
            assertThat(run.plan().getClassMap()).as("move of " + move.getKey()).containsEntry(move.getKey(), move.getValue().asString());
        }
        for (var move : expected.path("movedTests").properties()) {
            assertThat(run.plan().getClassMap()).as("test move of " + move.getKey()).containsEntry(move.getKey(), move.getValue().asString());
        }
        for (String kept : strings(expected.path("kept"))) {
            assertThat(run.plan().getClassMap()).as(kept + " stays in place").doesNotContainKey(kept);
            assertThat(entryOf(run, kept)).as("plan entry for " + kept).isPresent();
        }
        for (String manual : strings(expected.path("manualReview"))) {
            assertThat(entryOf(run, manual).orElseThrow().getSafety()).as("safety of " + manual).isEqualTo(SafetyLevel.MANUAL_REVIEW);
        }
        for (String unsupported : strings(expected.path("unsupported"))) {
            assertThat(entryOf(run, unsupported).orElseThrow().getSafety()).isEqualTo(SafetyLevel.UNSUPPORTED);
        }
        for (String file : strings(expected.path("manualReviewFiles"))) {
            TransformationPlanEntry entry = run.plan().getEntries().stream()
                    .filter(e -> e.getSourceFile().toString().equals(file)).findFirst().orElseThrow();
            assertThat(entry.getSafety()).isEqualTo(SafetyLevel.MANUAL_REVIEW);
            assertThat(entry.isMoved()).isFalse();
        }
        Set<String> conflictTypes = run.plan().getConflicts().stream().map(PlanConflict::type).map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
        if (expected.has("conflicts")) {
            assertThat(conflictTypes).containsExactlyInAnyOrderElementsOf(strings(expected.get("conflicts")));
        }
        for (String file : strings(expected.path("resourceFindings"))) {
            assertThat(run.plan().getResourceFindings()).anyMatch(f -> f.file().equals(file));
        }

        // ---- analysis
        assertThat(run.analysis().model().unparseableFiles()).hasSize(expected.path("parseErrors").asInt(0));
        assertThat(run.analysis().cycles().getCycleCount()).isGreaterThanOrEqualTo(expected.path("minCycles").asInt(0));
        for (String severity : strings(expected.path("cycleSeverities"))) {
            assertThat(run.analysis().cycles().getStructuredCycles()).anyMatch(c -> c.severity().name().equals(severity));
        }
        for (JsonNode edge : expected.path("edges")) {
            String source = edge.get(0).asString();
            String target = edge.get(1).asString();
            String type = edge.get(2).asString();
            assertThat(run.analysis().graph().getEdges())
                    .as("edge %s -[%s]-> %s", source, type, target)
                    .anyMatch((DependencyEdge e) -> e.getSource().getId().equals(source)
                            && e.getTarget().getId().equals(target) && e.getDependencyType().name().equals(type));
        }

        // ---- validation (levels 1-6; Maven is exercised by the end-to-end test)
        boolean parseErrors = expected.path("parseErrors").asInt(0) > 0;
        for (LevelResult level : run.validation().levels()) {
            switch (level.level()) {
                case FILESYSTEM, PACKAGE_CONSISTENCY, IMPORT_RESOLUTION, DEPENDENCY_GRAPH ->
                        assertThat(level.status()).as(level.level() + ": " + level.issues()).isEqualTo(ValidationStatus.PASS);
                case JAVA_PARSING -> assertThat(level.status()).as(level.issues().toString())
                        .isEqualTo(parseErrors ? ValidationStatus.WARN : ValidationStatus.PASS);
                case ARCHITECTURE_RULES -> assertThat(level.status()).isNotEqualTo(ValidationStatus.FAIL);
                case BUILD -> assertThat(level.status()).isEqualTo(ValidationStatus.SKIPPED);
            }
        }
        assertThat(run.validation().levels()).extracting(LevelResult::level).contains(ValidationLevel.values());

        // ---- the transformed project compiles (known-good fixtures)
        if (expected.path("compiles").asBoolean(false)) {
            List<String> errors = InMemoryCompiler.compile(run.transformed(), temp.resolve("classes"));
            assertThat(errors).as("compile errors of transformed " + fixture).isEmpty();
        }
        assertThat(Files.exists(run.transformed().resolve("pom.xml"))).isTrue();
    }

    private static Optional<TransformationPlanEntry> entryOf(PipelineRunner.Run run, String qualifiedName) {
        return run.plan().getEntries().stream()
                .filter(e -> e.getClasses().stream().anyMatch(c -> c.sourceQualifiedName().equals(qualifiedName)))
                .findFirst();
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> values.add(n.asString()));
        }
        return values;
    }
}
