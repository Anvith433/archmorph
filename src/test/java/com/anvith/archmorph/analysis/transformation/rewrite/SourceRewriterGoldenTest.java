package com.anvith.archmorph.analysis.transformation.rewrite;

import com.anvith.archmorph.analysis.registry.DefaultProjectClassRegistry;
import com.anvith.archmorph.analysis.registry.ProjectClassInfo;
import com.anvith.archmorph.analysis.registry.ProjectClassRegistry;
import com.anvith.archmorph.parser.ClassMetadata;
import com.anvith.archmorph.parser.ComponentAnalyzer;
import com.anvith.archmorph.parser.ComponentClassifier;
import com.anvith.archmorph.parser.JavaParserService;
import com.anvith.archmorph.parser.ParsedSource;
import com.anvith.archmorph.parser.SourceScope;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-file tests for {@link SourceRewriter}. Each case under {@code golden/rewriter/<case>} has an
 * {@code input/} source tree, a {@code moves.txt} class map and the reviewed {@code expected/} output
 * (placed at the file's new location). Run with {@code -Dgolden.update=true} to regenerate the expected
 * files after an intentional change, then review the diff before committing.
 */
class SourceRewriterGoldenTest {

    private static final Path ROOT = Paths.get("src", "test", "resources", "golden", "rewriter");
    private static final boolean UPDATE = Boolean.getBoolean("golden.update");

    private final JavaParserService parser = new JavaParserService();
    private final ComponentAnalyzer analyzer = new ComponentAnalyzer(new ComponentClassifier());
    private final SourceRewriter rewriter = new SourceRewriter(parser);

    static List<String> cases() throws IOException {
        try (Stream<Path> stream = Files.list(ROOT)) {
            return stream.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void rewriteMatchesGoldenFiles(String name) throws IOException {
        Path base = ROOT.resolve(name);
        Path input = base.resolve("input");
        Map<String, String> moves = readMoves(base.resolve("moves.txt"));

        List<Path> files;
        try (Stream<Path> stream = Files.walk(input)) {
            files = stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }

        ProjectClassRegistry registry = new DefaultProjectClassRegistry();
        Map<Path, ClassMetadata> primary = new LinkedHashMap<>();
        for (Path file : files) {
            ParsedSource parsed = parser.parse(parser.createParser(), file);
            assertThat(parsed.successful()).as("input parses: " + file).isTrue();
            List<ClassMetadata> types = analyzer.analyzeAll(parsed.compilationUnit(), file, input.relativize(file).toString(), SourceScope.MAIN);
            types.forEach(t -> registry.register(ProjectClassInfo.of(t)));
            String fileName = file.getFileName().toString().replace(".java", "");
            primary.put(file, types.stream().filter(t -> t.getClassName().equals(fileName)).findFirst().orElse(types.getFirst()));
        }

        for (Path file : files) {
            ClassMetadata type = primary.get(file);
            String target = moves.getOrDefault(type.getQualifiedName(), type.getQualifiedName());
            String newPackage = target.contains(".") ? target.substring(0, target.lastIndexOf('.')) : "";
            String source = Files.readString(file, StandardCharsets.UTF_8);

            RewriteResult result = rewriter.rewrite(source, newPackage, moves, registry);

            Path expected = base.resolve("expected")
                    .resolve(newPackage.isEmpty() ? "" : newPackage.replace('.', '/'))
                    .resolve(file.getFileName().toString());
            if (UPDATE) {
                Files.createDirectories(expected.getParent());
                Files.writeString(expected, result.source(), StandardCharsets.UTF_8);
                continue;
            }
            assertThat(expected).as("golden file for " + input.relativize(file)).exists();
            assertThat(result.source()).as("rewrite of " + input.relativize(file))
                    .isEqualTo(Files.readString(expected, StandardCharsets.UTF_8));

            // The rewrite must itself be valid Java.
            assertThat(parser.parse(parser.createParser(), result.source()).successful()).isTrue();
        }
    }

    private static Map<String, String> readMoves(Path file) throws IOException {
        Map<String, String> moves = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("=");
            moves.put(parts[0].trim(), parts[1].trim());
        }
        return moves;
    }
}
