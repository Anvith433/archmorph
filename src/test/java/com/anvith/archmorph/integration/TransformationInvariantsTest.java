package com.anvith.archmorph.integration;

import com.anvith.archmorph.analysis.model.SourceFile;
import com.anvith.archmorph.analysis.transformation.planner.ClassMove;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlan;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanEntry;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanner;
import com.anvith.archmorph.analysis.transformation.target.TargetStrategy;
import com.anvith.archmorph.parser.JavaParserService;
import com.anvith.archmorph.parser.ParsedSource;
import com.anvith.archmorph.support.ArchMorphTest;
import com.anvith.archmorph.support.Fixtures;
import com.anvith.archmorph.support.PipelineRunner;
import com.github.javaparser.ast.ImportDeclaration;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.anvith.archmorph.analysis.validation.LevelResult;
import com.anvith.archmorph.analysis.validation.ValidationIssue;
import com.anvith.archmorph.analysis.validation.ValidationLevel;
import com.anvith.archmorph.support.InMemoryCompiler;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invariants that must hold for every project, checked on every fixture:
 * no file disappears, one destination per class, one source per destination, package matches path,
 * no import to an old location, no collisions, the original is untouched and everything is deterministic.
 */
@ArchMorphTest
class TransformationInvariantsTest {

    @Autowired
    PipelineRunner runner;

    @Autowired
    TransformationPlanner planner;

    @Autowired
    JavaParserService parserService;

    @TempDir
    Path temp;

    static List<Arguments> fixtures() {
        List<Arguments> arguments = new java.util.ArrayList<>();
        for (String fixture : Fixtures.names()) {
            for (TargetStrategy strategy : TargetStrategy.values()) {
                arguments.add(Arguments.of(fixture, strategy));
            }
        }
        return arguments;
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("fixtures")
    void invariantsHold(String fixture, TargetStrategy strategy) throws Exception {
        PipelineRunner.Run run = runner.run(fixture, temp.resolve("a"), strategy);
        TransformationPlan plan = run.plan();

        // 1. No source file disappears: every Java file has exactly one entry and its target exists.
        Set<String> sources = new HashSet<>();
        for (SourceFile file : run.analysis().model().files()) {
            sources.add(file.getRelativePath());
        }
        Map<String, Integer> entriesPerSource = new HashMap<>();
        for (TransformationPlanEntry entry : plan.getEntries()) {
            entriesPerSource.merge(entry.getSourceFile().toString(), 1, Integer::sum);
            assertThat(run.transformed().resolve(entry.getTargetFile().toString())).as("target of " + entry).exists();
        }
        assertThat(entriesPerSource.keySet()).containsExactlyInAnyOrderElementsOf(sources);
        assertThat(entriesPerSource.values()).allMatch(count -> count == 1);

        // 2./3. One destination per class and one class per destination; no target path collision.
        Map<String, String> destinations = new HashMap<>();
        Set<String> targetPaths = new HashSet<>();
        for (TransformationPlanEntry entry : plan.getEntries()) {
            assertThat(targetPaths.add(entry.getTargetFile().toString())).as("unique target " + entry.getTargetFile()).isTrue();
            for (ClassMove move : entry.getClasses()) {
                String previous = destinations.put(move.targetQualifiedName(), move.sourceQualifiedName());
                assertThat(previous).as("two sources for " + move.targetQualifiedName()).isNull();
            }
        }

        // 4. Package declaration matches the directory; 5. no import to the old location of a moved class.
        Set<String> moved = plan.getClassMap().keySet();
        for (TransformationPlanEntry entry : plan.getEntries()) {
            Path file = run.transformed().resolve(entry.getTargetFile().toString());
            ParsedSource parsed = parserService.parse(parserService.createParser(), file);
            if (!parsed.successful()) {
                continue;
            }
            String pkg = parsed.compilationUnit().getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            assertThat(pkg).as("package of " + entry.getTargetFile()).isEqualTo(entry.getTargetPackage());
            String path = entry.getTargetFile().toString();
            String withinRoot = path.substring(path.indexOf("/java/") + 6);
            String dir = withinRoot.contains("/") ? withinRoot.substring(0, withinRoot.lastIndexOf('/')) : "";
            assertThat(dir.replace('/', '.')).isEqualTo(pkg);
            for (ImportDeclaration imp : parsed.compilationUnit().getImports()) {
                String name = imp.getNameAsString();
                String owner = imp.isStatic() && !imp.isAsterisk() ? name.substring(0, name.lastIndexOf('.')) : name;
                assertThat(moved).as("stale import " + name + " in " + path).doesNotContain(owner);
            }
        }

        // 6. The original project is never modified.
        String before = hash(Fixtures.path(fixture), true);
        assertThat(hash(run.original(), false)).isEqualTo(before);

        // 7. Determinism: same inputs give the same plan and byte-identical output.
        PipelineRunner.Run second = runner.run(fixture, temp.resolve("b"), strategy);
        assertThat(second.plan().fingerprint()).isEqualTo(plan.fingerprint());
        assertThat(hash(second.transformed(), false)).isEqualTo(hash(run.transformed(), false));
        TransformationPlan replanned = planner.plan(run.analysis().model(), run.analysis().graph(), run.analysis().suggestion(),
                strategy);
        assertThat(replanned.fingerprint()).isEqualTo(plan.fingerprint());

        // 8. Module boundaries: no class of one module uses an internal class of another (MODULAR_MONOLITH).
        LevelResult architecture = run.validation().levels().stream()
                .filter(l -> l.level() == ValidationLevel.ARCHITECTURE_RULES).findFirst().orElseThrow();
        assertThat(architecture.issues()).as("architecture errors " + architecture.issues())
                .noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);

        // 9. Known-good fixtures compile in every layout.
        if (Files.readString(Fixtures.path(fixture).resolve("expected.json")).contains("\"compiles\": true")) {
            assertThat(InMemoryCompiler.compile(run.transformed(), temp.resolve("classes"))).as("compile errors").isEmpty();
        }
    }

    private static String hash(Path root, boolean skipExpectations) throws IOException {
        Map<String, String> digests = new TreeMap<>();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                String relative = root.relativize(file).toString();
                if (skipExpectations && relative.equals("expected.json")) {
                    continue;
                }
                digests.put(relative, sha(Files.readAllBytes(file)));
            }
        }
        return sha(digests.toString().getBytes());
    }

    private static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
