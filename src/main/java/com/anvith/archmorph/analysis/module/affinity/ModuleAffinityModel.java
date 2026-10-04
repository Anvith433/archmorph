package com.anvith.archmorph.analysis.module.affinity;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.analysis.module.naming.DomainTerms;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.parser.ClassMetadata;
import com.anvith.archmorph.parser.ComponentType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Score-based affinity between two classes:
 *
 * <pre>
 * affinity(A, B) =  wD·dependency + wN·naming + wP·package + wE·entity
 *                 + wC·endpoint   + wT·typeUsage
 *                 − crossDomainPenalty · differentDomain
 * </pre>
 *
 * <ul>
 *   <li><b>dependency</b> (signals A, B, J, K): saturating function {@code w/(w+1)} of the summed edge
 *       weights between the two classes. Edges that follow the layer direction
 *       (controller → service → repository → entity) count fully, reverse edges half.</li>
 *   <li><b>naming</b> (C, F): token similarity of domain stems ({@code UserService} / {@code UserDto}).</li>
 *   <li><b>package</b> (D): similarity of the non-layer package segments.</li>
 *   <li><b>entity</b> (E): direct entity relationship, otherwise Jaccard similarity of the entity sets
 *       the two classes touch.</li>
 *   <li><b>endpoint</b> (G): controller request paths versus the other class' domain stem.</li>
 *   <li><b>typeUsage</b> (H, I): Jaccard similarity of non-generic neighbours (callers and callees);
 *       method-terminology tokens contribute through the stem comparison of neighbours.</li>
 *   <li><b>crossDomainPenalty</b>: both classes carry unrelated, non-generic domain stems.</li>
 * </ul>
 * Weights come from {@code archmorph.module-discovery.*} and are normalised to sum to 1.
 */
public class ModuleAffinityModel {

    private static final Set<String> LAYER_SEGMENTS = Set.of(
            "controller", "controllers", "web", "rest", "api", "service", "services", "repository", "repositories",
            "repo", "dao", "entity", "entities", "model", "models", "domain", "dto", "dtos", "payload", "request",
            "response", "config", "configuration", "security", "exception", "exceptions", "util", "utils", "mapper",
            "mappers", "impl", "component", "components", "filter", "filters", "common", "shared");

    private final DependencyGraph graph;
    private final Map<String, ClassMetadata> facts;
    private final ArchMorphProperties.ModuleDiscovery config;
    private final double wDependency;
    private final double wNaming;
    private final double wPackage;
    private final double wEntity;
    private final double wEndpoint;
    private final double wTypeUsage;
    private final String basePackage;

    private final Map<String, List<String>> tokenCache = new HashMap<>();
    private final Map<String, Set<String>> entityCache = new HashMap<>();
    private final Map<String, Set<String>> neighbourCache = new HashMap<>();

    public ModuleAffinityModel(DependencyGraph graph, Map<String, ClassMetadata> facts,
                               ArchMorphProperties.ModuleDiscovery config, String basePackage) {
        this.graph = graph;
        this.facts = facts;
        this.config = config;
        this.basePackage = basePackage == null ? "" : basePackage;
        double sum = config.getDependencyWeight() + config.getNamingWeight() + config.getPackageWeight()
                + config.getEntityWeight() + config.getEndpointWeight() + config.getTypeUsageWeight();
        if (sum <= 0) {
            sum = 1;
        }
        this.wDependency = config.getDependencyWeight() / sum;
        this.wNaming = config.getNamingWeight() / sum;
        this.wPackage = config.getPackageWeight() / sum;
        this.wEntity = config.getEntityWeight() / sum;
        this.wEndpoint = config.getEndpointWeight() / sum;
        this.wTypeUsage = config.getTypeUsageWeight() / sum;
    }

    public AffinityBreakdown affinity(DependencyNode a, DependencyNode b) {
        if (a.equals(b)) {
            return AffinityBreakdown.NONE;
        }
        double dependency = dependencyScore(a, b);
        double naming = namingScore(a, b);
        double pkg = packageScore(a, b);
        double entity = entityScore(a, b);
        double endpoint = endpointScore(a, b);
        double typeUsage = typeUsageScore(a, b);
        double penalty = crossDomain(a, b) ? 1.0 : 0.0;

        double total = wDependency * dependency + wNaming * naming + wPackage * pkg + wEntity * entity
                + wEndpoint * endpoint + wTypeUsage * typeUsage - config.getCrossDomainPenalty() * penalty;
        total = Math.max(0, Math.min(1, total));
        return new AffinityBreakdown(dependency, naming, pkg, entity, endpoint, typeUsage, penalty, total);
    }

    /** Domain tokens of a node (cached). */
    public List<String> tokens(DependencyNode node) {
        return tokenCache.computeIfAbsent(node.getId(), id -> DomainTerms.tokens(node.getClassName()));
    }

    public boolean hasDomainTerm(DependencyNode node) {
        List<String> tokens = tokens(node);
        return !tokens.isEmpty() && !DomainTerms.isGeneric(String.join("", tokens));
    }

    // ------------------------------------------------------------------ signals

    private double dependencyScore(DependencyNode a, DependencyNode b) {
        double weight = 0;
        for (DependencyEdge edge : graph.getOutgoingEdges(a)) {
            if (edge.getTarget().equals(b)) {
                weight += edge.getWeight() * direction(a, b);
            }
        }
        for (DependencyEdge edge : graph.getOutgoingEdges(b)) {
            if (edge.getTarget().equals(a)) {
                weight += edge.getWeight() * direction(b, a);
            }
        }
        return weight / (weight + 1.0);
    }

    /** Edges that follow the layer direction count fully; reversed edges half. */
    private double direction(DependencyNode source, DependencyNode target) {
        int s = rank(source);
        int t = rank(target);
        return s >= 0 && t >= 0 && s < t ? 0.5 : 1.0;
    }

    private static int rank(DependencyNode node) {
        if (node.getComponentType() == null) {
            return -1;
        }
        return switch (node.getComponentType()) {
            case CONTROLLER -> 3;
            case SERVICE -> 2;
            case REPOSITORY -> 1;
            case ENTITY -> 0;
            default -> -1;
        };
    }

    private double namingScore(DependencyNode a, DependencyNode b) {
        if (!hasDomainTerm(a) || !hasDomainTerm(b)) {
            return 0;
        }
        return DomainTerms.tokenSimilarity(tokens(a), tokens(b));
    }

    private double packageScore(DependencyNode a, DependencyNode b) {
        List<String> pa = domainPackage(a);
        List<String> pb = domainPackage(b);
        if (pa.isEmpty() || pb.isEmpty()) {
            return 0;
        }
        if (pa.equals(pb)) {
            return 1.0;
        }
        int common = 0;
        while (common < Math.min(pa.size(), pb.size()) && pa.get(common).equals(pb.get(common))) {
            common++;
        }
        return common == 0 ? 0 : 0.6 * common / Math.max(pa.size(), pb.size());
    }

    /** Package segments below the project base package that are not layer names. */
    List<String> domainPackage(DependencyNode node) {
        String pkg = node.getPackageName() == null ? "" : node.getPackageName();
        if (!basePackage.isEmpty() && pkg.startsWith(basePackage)) {
            pkg = pkg.substring(basePackage.length());
        }
        List<String> segments = new ArrayList<>();
        for (String segment : pkg.split("\\.")) {
            String lower = segment.toLowerCase(Locale.ROOT);
            if (!lower.isEmpty() && !LAYER_SEGMENTS.contains(lower)) {
                segments.add(lower);
            }
        }
        return segments;
    }

    private double entityScore(DependencyNode a, DependencyNode b) {
        for (DependencyEdge edge : graph.getOutgoingEdges(a)) {
            if (edge.getTarget().equals(b) && edge.getDependencyType() == DependencyType.ENTITY_RELATIONSHIP) {
                return 1.0;
            }
        }
        for (DependencyEdge edge : graph.getOutgoingEdges(b)) {
            if (edge.getTarget().equals(a) && edge.getDependencyType() == DependencyType.ENTITY_RELATIONSHIP) {
                return 1.0;
            }
        }
        Set<String> ea = entitiesOf(a);
        Set<String> eb = entitiesOf(b);
        return jaccard(ea, eb);
    }

    private Set<String> entitiesOf(DependencyNode node) {
        return entityCache.computeIfAbsent(node.getId(), id -> {
            Set<String> entities = new HashSet<>();
            if (node.getComponentType() == ComponentType.ENTITY) {
                entities.add(node.getId());
            }
            for (DependencyEdge edge : graph.getOutgoingEdges(node)) {
                DependencyNode target = edge.getTarget();
                if (target.getComponentType() == ComponentType.ENTITY && hasDomainTerm(target)) {
                    entities.add(target.getId());
                }
            }
            return entities;
        });
    }

    private double endpointScore(DependencyNode a, DependencyNode b) {
        List<String> pathsA = pathTerms(a);
        List<String> pathsB = pathTerms(b);
        if (!pathsA.isEmpty() && !pathsB.isEmpty()) {
            return pathsA.stream().anyMatch(pathsB::contains) ? 1.0 : 0;
        }
        if (!pathsA.isEmpty() && hasDomainTerm(b)) {
            return pathsA.contains(String.join("", tokens(b))) ? 1.0 : 0;
        }
        if (!pathsB.isEmpty() && hasDomainTerm(a)) {
            return pathsB.contains(String.join("", tokens(a))) ? 1.0 : 0;
        }
        return 0;
    }

    /** Domain terms of the request paths a controller declares. */
    public List<String> pathTerms(DependencyNode node) {
        ClassMetadata metadata = facts.get(node.getId());
        if (metadata == null || metadata.getEndpointPaths().isEmpty()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        metadata.getEndpointPaths().forEach(path -> terms.addAll(DomainTerms.pathTerms(path)));
        return terms;
    }

    private double typeUsageScore(DependencyNode a, DependencyNode b) {
        Set<String> na = neighbours(a);
        Set<String> nb = neighbours(b);
        na.remove(b.getId());
        nb.remove(a.getId());
        return jaccard(na, nb);
    }

    /** Non-generic callers and callees; shared utility types do not create similarity. */
    private Set<String> neighbours(DependencyNode node) {
        return new HashSet<>(neighbourCache.computeIfAbsent(node.getId(), id -> {
            Set<String> neighbours = new HashSet<>();
            for (DependencyNode n : graph.getSuccessors(node)) {
                if (hasDomainTerm(n)) {
                    neighbours.add(n.getId());
                }
            }
            for (DependencyNode n : graph.getPredecessors(node)) {
                if (hasDomainTerm(n)) {
                    neighbours.add(n.getId());
                }
            }
            return neighbours;
        }));
    }

    private boolean crossDomain(DependencyNode a, DependencyNode b) {
        if (!hasDomainTerm(a) || !hasDomainTerm(b)) {
            return false;
        }
        return DomainTerms.tokenSimilarity(tokens(a), tokens(b)) == 0;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }
}
