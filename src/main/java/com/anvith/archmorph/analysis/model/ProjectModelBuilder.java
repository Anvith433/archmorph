package com.anvith.archmorph.analysis.model;

import com.anvith.archmorph.analysis.registry.DefaultProjectClassRegistry;
import com.anvith.archmorph.analysis.registry.ProjectClassCollector;
import com.anvith.archmorph.analysis.registry.ProjectClassRegistry;
import com.anvith.archmorph.analysis.scanner.SourceScanner;
import com.anvith.archmorph.common.exception.SourceCodeNotFoundException;
import com.anvith.archmorph.parser.ClassMetadata;
import com.anvith.archmorph.parser.ComponentAnalyzer;
import com.anvith.archmorph.parser.JavaParserService;
import com.anvith.archmorph.parser.ParsedSource;
import com.anvith.archmorph.parser.ProjectStructure;
import com.anvith.archmorph.parser.SourceScope;
import com.github.javaparser.JavaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans, parses and registers every Java source of a project in a single
 * pass (the original pipeline parsed every file twice).
 */
@Service
public class ProjectModelBuilder {

    private static final Logger log = LoggerFactory.getLogger(ProjectModelBuilder.class);

    private final SourceScanner sourceScanner;
    private final JavaParserService javaParserService;
    private final ComponentAnalyzer componentAnalyzer;

    public ProjectModelBuilder(SourceScanner sourceScanner, JavaParserService javaParserService,
                               ComponentAnalyzer componentAnalyzer) {
        this.sourceScanner = sourceScanner;
        this.javaParserService = javaParserService;
        this.componentAnalyzer = componentAnalyzer;
    }

    public ProjectModel build(ProjectStructure structure, int maxJavaFiles, boolean symbolSolver) {
        List<Path> roots = new ArrayList<>(structure.mainSourceRoots());
        roots.addAll(structure.testSourceRoots());
        JavaParser parser = javaParserService.createParser(roots, symbolSolver);

        List<SourceFile> files = new ArrayList<>();
        int budget = maxJavaFiles;
        for (Path root : structure.mainSourceRoots()) {
            List<Path> sources = sourceScanner.scan(List.of(root), budget);
            budget -= sources.size();
            sources.forEach(path -> files.add(parseFile(parser, structure.projectRoot(), root, path, SourceScope.MAIN)));
        }
        for (Path root : structure.testSourceRoots()) {
            List<Path> sources = sourceScanner.scan(List.of(root), Math.max(budget, 0));
            budget -= sources.size();
            sources.forEach(path -> files.add(parseFile(parser, structure.projectRoot(), root, path, SourceScope.TEST)));
        }

        if (files.stream().noneMatch(f -> f.getScope() == SourceScope.MAIN)) {
            throw new SourceCodeNotFoundException("No Java source files were found under src/main/java.");
        }

        ProjectClassRegistry registry = new DefaultProjectClassRegistry();
        ProjectClassCollector collector = new ProjectClassCollector(registry);
        files.forEach(file -> file.getTypes().forEach(collector::collect));

        long unparseable = files.stream().filter(f -> !f.isParseable()).count();
        log.info("Parsed {} Java files ({} unparseable), {} types registered",
                files.size(), unparseable, registry.all().size());
        return new ProjectModel(structure, List.copyOf(files), registry);
    }

    private SourceFile parseFile(JavaParser parser, Path projectRoot, Path sourceRoot, Path file, SourceScope scope) {
        String relative = projectRoot.relativize(file).toString().replace('\\', '/');
        ParsedSource parsed = javaParserService.parse(parser, file);
        List<ClassMetadata> types = parsed.compilationUnit() == null
                ? List.of()
                : componentAnalyzer.analyzeAll(parsed.compilationUnit(), file, relative, scope);
        return new SourceFile(relative, file, sourceRoot, scope, parsed, types);
    }
}
