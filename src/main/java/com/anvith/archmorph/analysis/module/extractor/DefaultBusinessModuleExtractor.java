package com.anvith.archmorph.analysis.module.extractor;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.module.ClassAssignment;
import com.anvith.archmorph.analysis.module.ModuleCategory;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.module.affinity.AffinityBreakdown;
import com.anvith.archmorph.analysis.module.affinity.ModuleAffinityModel;
import com.anvith.archmorph.analysis.module.naming.ModuleNamingStrategy;
import com.anvith.archmorph.analysis.transformation.packaging.BasePackageResolver;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.parser.ClassMetadata;
import com.anvith.archmorph.parser.ComponentType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Multi-signal module discovery.
 *
 * <ol>
 *   <li><b>Fixed roles.</b> Application entry points, configuration, security and exception
 *       handlers never join a business module.</li>
 *   <li><b>Domain clusters.</b> Remaining classes with a domain stem (User, UserService, UserDto,
 *       UserController...) form one cluster per stem. Clusters whose stem extends another
 *       cluster's stem ({@code OrderItem} → {@code Order}) are merged when the base cluster is
 *       anchored by an entity, controller or service.</li>
 *   <li><b>Ambiguous classes</b> (no domain stem: {@code ApiResponse}, {@code Auditable}, helpers)
 *       are placed by the affinity model: used by many modules → shared; used by one module →
 *       that module; otherwise shared with a warning.</li>
 * </ol>
 * Shared classes are placed in {@code shared} exactly once and are never duplicated.
 */
@Service
public class DefaultBusinessModuleExtractor implements BusinessModuleExtractor {

    private static final Set<String> INFRA_SEGMENTS = Set.of(
            "infrastructure", "infra", "client", "clients", "adapter", "adapters", "integration", "messaging",
            "storage", "cache", "gateway", "gateways", "external");

    private final ArchMorphProperties properties;
    private final BasePackageResolver basePackageResolver;
    private final ModuleNamingStrategy namingStrategy;

    public DefaultBusinessModuleExtractor(ArchMorphProperties properties, BasePackageResolver basePackageResolver,
                                          ModuleNamingStrategy namingStrategy) {
        this.properties = properties;
        this.basePackageResolver = basePackageResolver;
        this.namingStrategy = namingStrategy;
    }

    private record Fixed(ModuleCategory category, String reason) {
    }

    @Override
    public ModuleDiscoveryReport extract(DependencyGraph graph, Map<String, ClassMetadata> facts) {
        ArchMorphProperties.ModuleDiscovery config = properties.getModuleDiscovery();
        ModuleDiscoveryReport report = new ModuleDiscoveryReport();

        List<DependencyNode> nodes = new ArrayList<>(graph.getInternalNodes());
        nodes.sort(Comparator.comparing(DependencyNode::getId));

        Set<String> packages = new HashSet<>();
        nodes.forEach(n -> packages.add(n.getPackageName() == null ? "" : n.getPackageName()));
        ModuleAffinityModel model = new ModuleAffinityModel(graph, facts, config, basePackageResolver.resolve(packages));

        // 1. Fixed roles
        Map<DependencyNode, Fixed> fixed = new LinkedHashMap<>();
        List<DependencyNode> remaining = new ArrayList<>();
        for (DependencyNode node : nodes) {
            Fixed role = fixedRole(node, model);
            if (role != null) {
                fixed.put(node, role);
            } else {
                remaining.add(node);
            }
        }

        // 2. Domain stem clusters
        Map<String, List<DependencyNode>> clusters = new TreeMap<>();
        List<DependencyNode> ambiguous = new ArrayList<>();
        for (DependencyNode node : remaining) {
            if (model.hasDomainTerm(node)) {
                clusters.computeIfAbsent(String.join("", model.tokens(node)), k -> new ArrayList<>()).add(node);
            } else {
                ambiguous.add(node);
            }
        }
        mergePrefixClusters(clusters, model);

        Map<DependencyNode, String> clusterOf = new HashMap<>();
        for (Map.Entry<String, List<DependencyNode>> cluster : clusters.entrySet()) {
            String moduleName = namingStrategy.toPackageSegment(cluster.getKey());
            for (DependencyNode node : cluster.getValue()) {
                clusterOf.put(node, moduleName);
                report.assign(node, moduleName, ModuleCategory.BUSINESS_MODULE, stemConfidence(node),
                        ClassAssignment.Origin.AUTOMATIC,
                        List.of("domain term '" + cluster.getKey() + "' from class name " + node.getClassName()));
            }
        }

        // 3. Fixed roles into shared / application
        for (Map.Entry<DependencyNode, Fixed> entry : fixed.entrySet()) {
            Fixed role = entry.getValue();
            String module = role.category() == ModuleCategory.APPLICATION
                    ? ModuleDiscoveryReport.APPLICATION : ModuleDiscoveryReport.SHARED;
            report.assign(entry.getKey(), module, role.category(), 0.9, ClassAssignment.Origin.AUTOMATIC,
                    List.of(role.reason()));
        }

        // 4. Ambiguous classes through the affinity model (two passes so placements can propagate)
        List<DependencyNode> pending = new ArrayList<>(ambiguous);
        for (int pass = 0; pass < 2 && !pending.isEmpty(); pass++) {
            List<DependencyNode> next = new ArrayList<>();
            for (DependencyNode node : pending) {
                if (!placeAmbiguous(node, graph, model, config, clusterOf, report)) {
                    next.add(node);
                }
            }
            pending = next;
        }
        for (DependencyNode node : pending) {
            ModuleCategory category = infrastructure(node) ? ModuleCategory.INFRASTRUCTURE : ModuleCategory.SHARED;
            report.assign(node, ModuleDiscoveryReport.SHARED, category, 0.4, ClassAssignment.Origin.AUTOMATIC,
                    List.of("no domain term and no dependency links to a business module; placed in shared"));
            report.getWarnings().add(node.getClassName() + " could not be tied to a business module and was placed in shared.");
        }

        report.pruneEmptyModules();
        return report;
    }

    private boolean placeAmbiguous(DependencyNode node, DependencyGraph graph, ModuleAffinityModel model,
                                   ArchMorphProperties.ModuleDiscovery config, Map<DependencyNode, String> clusterOf,
                                   ModuleDiscoveryReport report) {
        Set<DependencyNode> neighbours = new HashSet<>(graph.getSuccessors(node));
        neighbours.addAll(graph.getPredecessors(node));

        Map<String, Double> bestAffinity = new TreeMap<>();
        Map<String, AffinityBreakdown> bestBreakdown = new HashMap<>();
        Set<String> touchedModules = new TreeSet<>();
        for (DependencyNode neighbour : neighbours) {
            String module = clusterOf.get(neighbour);
            if (module == null) {
                module = businessModuleOf(report, neighbour);
            }
            if (module == null) {
                continue;
            }
            touchedModules.add(module);
            AffinityBreakdown breakdown = model.affinity(node, neighbour);
            if (breakdown.total() > bestAffinity.getOrDefault(module, -1.0)) {
                bestAffinity.put(module, breakdown.total());
                bestBreakdown.put(module, breakdown);
            }
        }

        boolean infra = infrastructure(node);
        if (touchedModules.size() >= config.getSharedUsageThreshold()) {
            ModuleCategory category = infra ? ModuleCategory.INFRASTRUCTURE : ModuleCategory.SHARED;
            report.assign(node, ModuleDiscoveryReport.SHARED, category, 0.85, ClassAssignment.Origin.AUTOMATIC,
                    List.of("no domain term; used by " + touchedModules.size() + " modules " + touchedModules));
            return true;
        }
        if (touchedModules.isEmpty()) {
            return false;
        }
        if (touchedModules.size() == 1 && !infra) {
            String module = touchedModules.iterator().next();
            assignToModule(node, module, bestBreakdown.get(module), 0.6, clusterOf, report,
                    "only used by module '" + module + "'");
            return true;
        }
        if (touchedModules.size() >= 2) {
            // Used by two modules: sharing is safer than choosing one owner.
            ModuleCategory category = infra ? ModuleCategory.INFRASTRUCTURE : ModuleCategory.SHARED;
            report.assign(node, ModuleDiscoveryReport.SHARED, category, 0.7, ClassAssignment.Origin.AUTOMATIC,
                    List.of("no domain term; used by modules " + touchedModules));
            return true;
        }
        report.assign(node, ModuleDiscoveryReport.SHARED, ModuleCategory.INFRASTRUCTURE, 0.6,
                ClassAssignment.Origin.AUTOMATIC, List.of("infrastructure-style class used by a single module"));
        return true;
    }

    private void assignToModule(DependencyNode node, String module, AffinityBreakdown breakdown, double base,
                                Map<DependencyNode, String> clusterOf, ModuleDiscoveryReport report, String reason) {
        double confidence = Math.min(0.9, base + (breakdown == null ? 0 : breakdown.total() * 0.3));
        clusterOf.put(node, module);
        List<String> reasons = new ArrayList<>(List.of(reason));
        if (breakdown != null) {
            reasons.add("affinity: " + breakdown.explain());
        }
        report.assign(node, module, ModuleCategory.BUSINESS_MODULE, confidence, ClassAssignment.Origin.AUTOMATIC, reasons);
    }

    private String businessModuleOf(ModuleDiscoveryReport report, DependencyNode node) {
        ClassAssignment assignment = report.getAssignment(node.getId());
        return assignment != null && assignment.category() == ModuleCategory.BUSINESS_MODULE ? assignment.moduleName() : null;
    }

    private Fixed fixedRole(DependencyNode node, ModuleAffinityModel model) {
        ComponentType type = node.getComponentType() == null ? ComponentType.UNKNOWN : node.getComponentType();
        return switch (type) {
            case APPLICATION -> new Fixed(ModuleCategory.APPLICATION, "application entry point");
            case CONFIGURATION -> new Fixed(ModuleCategory.CONFIGURATION, "framework configuration class");
            case SECURITY, FILTER -> new Fixed(ModuleCategory.SECURITY, "security / request-filter class");
            case EXCEPTION_HANDLER -> new Fixed(ModuleCategory.SHARED, "global exception handler");
            default -> null;
        };
    }

    private boolean infrastructure(DependencyNode node) {
        String pkg = node.getPackageName() == null ? "" : node.getPackageName().toLowerCase(Locale.ROOT);
        for (String segment : pkg.split("\\.")) {
            if (INFRA_SEGMENTS.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    /** Merge "orderitem" into "order" when the shorter stem is anchored by an entity, controller or service. */
    private void mergePrefixClusters(Map<String, List<DependencyNode>> clusters, ModuleAffinityModel model) {
        List<String> keys = new ArrayList<>(clusters.keySet());
        keys.sort(Comparator.comparingInt((String k) -> tokenCount(clusters.get(k), model)).reversed().thenComparing(k -> k));
        for (String key : keys) {
            List<DependencyNode> members = clusters.get(key);
            if (members == null || members.isEmpty()) {
                continue;
            }
            List<String> tokens = model.tokens(members.getFirst());
            for (int prefixLength = tokens.size() - 1; prefixLength >= 1; prefixLength--) {
                String prefixKey = String.join("", tokens.subList(0, prefixLength));
                List<DependencyNode> base = clusters.get(prefixKey);
                if (base != null && anchored(base)) {
                    base.addAll(members);
                    clusters.remove(key);
                    break;
                }
            }
        }
    }

    private int tokenCount(List<DependencyNode> members, ModuleAffinityModel model) {
        return members.isEmpty() ? 0 : model.tokens(members.getFirst()).size();
    }

    private boolean anchored(List<DependencyNode> cluster) {
        return cluster.stream().anyMatch(n -> n.getComponentType() == ComponentType.ENTITY
                || n.getComponentType() == ComponentType.CONTROLLER || n.getComponentType() == ComponentType.SERVICE);
    }

    private double stemConfidence(DependencyNode node) {
        ComponentType type = node.getComponentType();
        if (type == ComponentType.ENTITY || type == ComponentType.CONTROLLER || type == ComponentType.SERVICE) {
            return 0.9;
        }
        if (type == ComponentType.REPOSITORY || type == ComponentType.DTO) {
            return 0.85;
        }
        return 0.7;
    }
}
