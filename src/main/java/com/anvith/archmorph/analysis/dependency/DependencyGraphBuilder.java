package com.anvith.archmorph.analysis.dependency;

import com.anvith.archmorph.analysis.dependency.extractor.DependencyExtractor;
import com.anvith.archmorph.analysis.dependency.extractor.ExtractionContext;
import com.anvith.archmorph.analysis.dependency.extractor.ResolutionStatistics;
import com.anvith.archmorph.analysis.dependency.resolve.TypeContext;
import com.anvith.archmorph.analysis.dependency.resolve.TypeResolver;
import com.anvith.archmorph.analysis.model.ProjectModel;
import com.anvith.archmorph.analysis.model.SourceFile;
import com.anvith.archmorph.parser.ClassMetadata;
import com.anvith.archmorph.parser.SourceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the project dependency graph from a parsed {@link ProjectModel}.
 *
 * <p>Nodes are the top-level types of production sources. Test sources are
 * not part of the architecture graph (they are handled as a separate
 * transformation scope).</p>
 */
@Service
public class DependencyGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    private final List<DependencyExtractor> extractors;

    public DependencyGraphBuilder(List<DependencyExtractor> extractors) {
        this.extractors = List.copyOf(extractors);
    }

    public record Result(DependencyGraph graph, ResolutionStatistics statistics) {
    }

    public Result build(ProjectModel model, Set<String> additionalIgnoredTypes, boolean symbolSolver) {
        return build(model, SourceScope.MAIN, additionalIgnoredTypes, symbolSolver);
    }

    public Result build(ProjectModel model, SourceScope scope, Set<String> additionalIgnoredTypes, boolean symbolSolver) {
        DependencyGraph graph = new DependencyGraph();
        ResolutionStatistics statistics = new ResolutionStatistics();
        TypeResolver resolver = new TypeResolver(model.registry(), additionalIgnoredTypes);

        Map<String, DependencyNode> nodes = new HashMap<>();
        for (SourceFile file : model.files()) {
            if (file.getScope() != SourceScope.MAIN && file.getScope() != scope) {
                continue;
            }
            for (ClassMetadata type : file.getTopLevelTypes()) {
                DependencyNode node = toNode(type);
                nodes.put(type.getQualifiedName(), node);
                if (file.getScope() == scope) {
                    graph.addNode(node);
                }
            }
        }

        for (SourceFile file : model.files()) {
            if (file.getScope() != scope || !file.isParseable()) {
                continue;
            }
            TypeContext typeContext = TypeContext.of(file.getParsed().compilationUnit());
            for (ClassMetadata type : file.getTopLevelTypes()) {
                DependencyNode source = nodes.get(type.getQualifiedName());
                ExtractionContext context = new ExtractionContext(type.getDeclaration(), type, source, graph,
                        resolver, typeContext, nodes::get, symbolSolver, statistics);
                for (DependencyExtractor extractor : extractors) {
                    extractor.extract(context);
                }
            }
        }

        log.debug("Dependency graph built: {} nodes, {} edges, {} low-confidence resolutions",
                graph.getNodeCount(), graph.getEdgeCount(), statistics.getLowConfidenceResolutions());
        return new Result(graph, statistics);
    }

    public static DependencyNode toNode(ClassMetadata type) {
        DependencyNode node = new DependencyNode(type.getQualifiedName(), type.getClassName(),
                type.getPackageName(), type.getComponentType());
        node.setClassificationConfidence(type.getClassification() == null ? 0 : type.getClassification().confidence());
        node.setRelativePath(type.getRelativePath());
        node.setSourceFile(type.getSourceFile());
        node.setCompilationUnit(type.getCompilationUnit());
        return node;
    }
}
