package com.anvith.archmorph.support;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyGraphBuilder;
import com.anvith.archmorph.analysis.dependency.extractor.AnnotationDependencyExtractor;
import com.anvith.archmorph.analysis.dependency.extractor.ConstructorDependencyExtractor;
import com.anvith.archmorph.analysis.dependency.extractor.FieldDependencyExtractor;
import com.anvith.archmorph.analysis.dependency.extractor.InheritanceDependencyExtractor;
import com.anvith.archmorph.analysis.dependency.extractor.MethodInvocationDependencyExtractor;
import com.anvith.archmorph.analysis.dependency.extractor.MethodSignatureDependencyExtractor;
import com.anvith.archmorph.analysis.dependency.extractor.ObjectCreationDependencyExtractor;
import com.anvith.archmorph.analysis.dependency.extractor.TypeReferenceDependencyExtractor;
import com.anvith.archmorph.analysis.model.ProjectModel;
import com.anvith.archmorph.analysis.model.ProjectModelBuilder;
import com.anvith.archmorph.analysis.scanner.SourceScanner;
import com.anvith.archmorph.parser.ComponentAnalyzer;
import com.anvith.archmorph.parser.ComponentClassifier;
import com.anvith.archmorph.parser.JavaParserService;
import com.anvith.archmorph.parser.ProjectStructureDetector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds small in-test projects without a Spring context. */
public final class Projects {

    private Projects() {
    }

    /** Write {@code files} (path relative to src/main/java → content) plus a pom and parse the project. */
    public static ProjectModel model(Path dir, Map<String, String> files) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pom.xml"), "<project><groupId>t</groupId><artifactId>t</artifactId></project>");
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path path = dir.resolve("src/main/java").resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
        ProjectModelBuilder builder = new ProjectModelBuilder(new SourceScanner(), new JavaParserService(),
                new ComponentAnalyzer(new ComponentClassifier()));
        return builder.build(new ProjectStructureDetector().detect(dir), 10_000, true);
    }

    public static DependencyGraph graph(ProjectModel model) {
        DependencyGraphBuilder builder = new DependencyGraphBuilder(List.of(new FieldDependencyExtractor(),
                new ConstructorDependencyExtractor(), new MethodSignatureDependencyExtractor(),
                new MethodInvocationDependencyExtractor(), new ObjectCreationDependencyExtractor(),
                new InheritanceDependencyExtractor(), new AnnotationDependencyExtractor(),
                new TypeReferenceDependencyExtractor(),
                new com.anvith.archmorph.analysis.dependency.extractor.EmbeddedJavaDependencyExtractor()));
        return builder.build(model, Set.of(), true).graph();
    }
}
