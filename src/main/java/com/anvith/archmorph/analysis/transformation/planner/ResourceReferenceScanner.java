package com.anvith.archmorph.analysis.transformation.planner;

import com.anvith.archmorph.parser.ProjectStructure;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds configuration and resource files that mention moved packages or classes.
 * Configuration is never modified blindly: findings are surfaced for a human decision.
 */
@Component
public class ResourceReferenceScanner {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "properties", "yml", "yaml", "xml", "factories", "imports", "json", "txt", "conf", "gradle");

    private static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_FINDINGS = 200;

    /**
     * @param tokens old qualified class names and old package names that no longer resolve after the move
     */
    public List<ResourceFinding> scan(ProjectStructure structure, Set<String> tokens) {
        List<ResourceFinding> findings = new ArrayList<>();
        if (tokens.isEmpty()) {
            return findings;
        }
        Pattern pattern = Pattern.compile("(?<![A-Za-z0-9_$])(" + String.join("|",
                tokens.stream().sorted((a, b) -> b.length() - a.length()).map(Pattern::quote).toList())
                + ")(?![A-Za-z0-9_$])");

        Path root = structure.projectRoot();
        List<Path> candidates = new ArrayList<>();
        candidates.add(root.resolve("pom.xml"));
        structure.resourceRoots().forEach(r -> collect(r, candidates, false));
        structure.mainSourceRoots().forEach(r -> collect(r, candidates, true));

        for (Path file : candidates) {
            if (findings.size() >= MAX_FINDINGS || !Files.isRegularFile(file)) {
                continue;
            }
            scanFile(root, file, pattern, findings);
        }
        return findings;
    }

    private void collect(Path directory, List<Path> sink, boolean nonJavaOnly) {
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !Files.isSymbolicLink(p))
                    .filter(p -> isText(p) && !(nonJavaOnly && p.toString().endsWith(".java")))
                    .sorted()
                    .forEach(sink::add);
        } catch (IOException ignored) {
            // unreadable directory: nothing to scan
        }
    }

    private boolean isText(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && TEXT_EXTENSIONS.contains(name.substring(dot + 1));
    }

    private void scanFile(Path root, Path file, Pattern pattern, List<ResourceFinding> findings) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String relative = root.relativize(file).toString().replace('\\', '/');
            for (int i = 0; i < lines.size() && findings.size() < MAX_FINDINGS; i++) {
                var matcher = pattern.matcher(lines.get(i));
                if (matcher.find()) {
                    String snippet = lines.get(i).strip();
                    findings.add(new ResourceFinding(relative, i + 1, matcher.group(1),
                            snippet.length() > 160 ? snippet.substring(0, 160) + "…" : snippet));
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // binary or undecodable file: skip
        }
    }
}
