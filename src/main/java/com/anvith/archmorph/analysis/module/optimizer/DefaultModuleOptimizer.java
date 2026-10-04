package com.anvith.archmorph.analysis.module.optimizer;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.dependency.DependencyType;
import com.anvith.archmorph.analysis.module.ClassAssignment;
import com.anvith.archmorph.analysis.module.ModuleCategory;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.module.ModuleInfo;
import com.anvith.archmorph.analysis.module.ModuleMetricsCalculator;
import com.anvith.archmorph.analysis.module.naming.DomainTerms;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.parser.ComponentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Improves the raw discovery result. Rules, in order:
 *
 * <ol>
 *   <li><b>Merge fragments</b>: a module without a controller or entity and with at most three classes
 *       is merged into the module that owns at least 60% of its dependencies. A fragment without any
 *       dependency is moved to {@code shared} with a warning.</li>
 *   <li><b>Promote shared</b>: a generic-named class used by at least {@code shared-usage-threshold}
 *       business modules is moved to {@code shared}. Domain-named classes are never moved because
 *       other modules use them; that is reported as a cross-module dependency instead.</li>
 *   <li><b>Metrics</b> are recalculated.</li>
 *   <li><b>Warnings</b>: suspicious singleton modules, oversized modules, low cohesion and high
 *       coupling. Module size alone never triggers a change.</li>
 * </ol>
 * Classes locked by the user are never moved.
 */
@Service
public class DefaultModuleOptimizer implements ModuleOptimizer {

    private static final Logger log = LoggerFactory.getLogger(DefaultModuleOptimizer.class);

    /** A module with a controller is a module in its own right, not a fragment. */
    private static final Set<ComponentType> ANCHORS = EnumSet.of(ComponentType.CONTROLLER);

    /** An entity anchors a module only together with code that works on it. */
    private static final Set<ComponentType> ENTITY_COMPANIONS = EnumSet.of(ComponentType.SERVICE, ComponentType.REPOSITORY,
            ComponentType.CONTROLLER);

    private static final Set<DependencyType> SUBTYPING = EnumSet.of(DependencyType.INHERITANCE, DependencyType.IMPLEMENTATION);

    private final ArchMorphProperties properties;
    private final ModuleMetricsCalculator metricsCalculator;

    public DefaultModuleOptimizer(ArchMorphProperties properties, ModuleMetricsCalculator metricsCalculator) {
        this.properties = properties;
        this.metricsCalculator = metricsCalculator;
    }

    @Override
    public ModuleDiscoveryReport optimize(ModuleDiscoveryReport input, DependencyGraph graph) {
        ModuleDiscoveryReport report = input.copy();
        ArchMorphProperties.ModuleDiscovery config = properties.getModuleDiscovery();

        promoteSharedSupertypes(report, graph);
        mergeFragments(report, graph);
        promoteShared(report, graph, config);
        moveAdaptersToTheirPort(report, graph);
        moveCompositionRoots(report, graph);
        enforceSingleOwnership(report);
        report.pruneEmptyModules();
        metricsCalculator.calculate(report, graph);
        addWarnings(report, config);
        return report;
    }

    private void mergeFragments(ModuleDiscoveryReport report, DependencyGraph graph) {
        boolean changed = true;
        while (changed) {
            changed = false;
            List<ModuleInfo> candidates = new ArrayList<>(report.getBusinessModules());
            candidates.sort(Comparator.comparingInt(ModuleInfo::getClassCount).thenComparing(ModuleInfo::getModuleName));
            for (ModuleInfo fragment : candidates) {
                if (fragment.getClassCount() > 3 || isAnchored(fragment) || anyLocked(report, fragment)) {
                    continue;
                }
                Map<String, Integer> weights = dependencyWeightsByModule(report, graph, fragment);
                int total = weights.values().stream().mapToInt(Integer::intValue).sum();
                if (total == 0 && !hasDependencies(graph, fragment)) {
                    for (DependencyNode node : new ArrayList<>(fragment.getClasses())) {
                        report.assign(node, ModuleDiscoveryReport.SHARED, ModuleCategory.SHARED, 0.4,
                                ClassAssignment.Origin.AUTOMATIC,
                                List.of("isolated class without dependencies or business anchors; placed in shared"));
                    }
                    report.getWarnings().add("Module '" + fragment.getModuleName()
                            + "' had no dependencies and no controller or entity; its classes were placed in shared.");
                    report.pruneEmptyModules();
                    changed = true;
                    break;
                }
                if (total == 0) {
                    continue;
                }
                Set<String> users = businessUsers(report, graph, fragment);
                Map.Entry<String, Integer> best = weights.entrySet().stream()
                        .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                        .orElseThrow();
                ModuleInfo target = report.getModule(best.getKey());
                if (target == null || !target.isBusinessModule() || best.getValue() < 0.6 * total) {
                    if (users.size() >= 2 && dependsOnlyOnShared(report, graph, fragment)) {
                        // e.g. an error-response DTO used by the controllers of several modules
                        for (DependencyNode node : new ArrayList<>(fragment.getClasses())) {
                            report.assign(node, ModuleDiscoveryReport.SHARED, ModuleCategory.SHARED, 0.6,
                                    ClassAssignment.Origin.AUTOMATIC,
                                    List.of("used by modules " + users + " and owned by none of them; placed in shared"));
                        }
                        report.pruneEmptyModules();
                        changed = true;
                        break;
                    }
                    continue;
                }
                log.debug("Merging fragment module {} into {}", fragment.getModuleName(), target.getModuleName());
                for (DependencyNode node : new ArrayList<>(fragment.getClasses())) {
                    report.assign(node, target.getModuleName(), ModuleCategory.BUSINESS_MODULE, 0.65,
                            ClassAssignment.Origin.AUTOMATIC,
                            List.of("merged fragment '" + fragment.getModuleName() + "': "
                                    + best.getValue() + " of " + total + " dependencies point to '"
                                    + target.getModuleName() + "'"));
                }
                report.pruneEmptyModules();
                changed = true;
                break;
            }
        }
    }

    private Map<String, Integer> dependencyWeightsByModule(ModuleDiscoveryReport report, DependencyGraph graph, ModuleInfo module) {
        Map<String, Integer> weights = new TreeMap<>();
        for (DependencyNode node : module.getClasses()) {
            for (DependencyEdge edge : graph.getOutgoingEdges(node)) {
                count(report, module, weights, edge.getTarget());
            }
            for (DependencyEdge edge : graph.getIncomingEdges(node)) {
                count(report, module, weights, edge.getSource());
            }
        }
        return weights;
    }

    private void count(ModuleDiscoveryReport report, ModuleInfo own, Map<String, Integer> weights, DependencyNode other) {
        String module = report.moduleOf(other.getId());
        if (module != null && !module.equals(own.getModuleName())) {
            ModuleInfo target = report.getModule(module);
            if (target != null && target.isBusinessModule()) {
                weights.merge(module, 1, Integer::sum);
            }
        }
    }

    private boolean isAnchored(ModuleInfo module) {
        boolean controller = module.getClasses().stream().anyMatch(n -> n.getComponentType() != null && ANCHORS.contains(n.getComponentType()));
        boolean entity = module.getClasses().stream().anyMatch(n -> n.getComponentType() == ComponentType.ENTITY);
        boolean companion = module.getClasses().stream().anyMatch(n -> n.getComponentType() != null
                && ENTITY_COMPANIONS.contains(n.getComponentType()));
        return controller || (entity && companion);
    }

    /** Business modules (other than its own) whose classes use the module. */
    private Set<String> businessUsers(ModuleDiscoveryReport report, DependencyGraph graph, ModuleInfo module) {
        Set<String> users = new java.util.TreeSet<>();
        for (DependencyNode node : module.getClasses()) {
            for (DependencyNode caller : graph.getPredecessors(node)) {
                String owner = report.moduleOf(caller.getId());
                ModuleInfo ownerModule = owner == null ? null : report.getModule(owner);
                if (ownerModule != null && ownerModule.isBusinessModule() && !owner.equals(module.getModuleName())) {
                    users.add(owner);
                }
            }
        }
        return users;
    }

    /** True when the module's classes depend on nothing but shared/application code (or nothing at all). */
    private boolean dependsOnlyOnShared(ModuleDiscoveryReport report, DependencyGraph graph, ModuleInfo module) {
        for (DependencyNode node : module.getClasses()) {
            for (DependencyNode target : graph.getSuccessors(node)) {
                String owner = report.moduleOf(target.getId());
                ModuleInfo targetModule = owner == null ? null : report.getModule(owner);
                if (targetModule != null && targetModule.isBusinessModule() && !owner.equals(module.getModuleName())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A base class or interface extended by classes of several business modules ({@code BaseEntity},
     * {@code Person} for {@code Owner} and {@code Vet}) cannot live in any one of them without making the
     * others depend on it: it belongs in shared. Repeats until stable, because moving a type can make its own
     * supertype shared as well. Supertypes that depend on business modules stay put (with a warning).
     */
    private void promoteSharedSupertypes(ModuleDiscoveryReport report, DependencyGraph graph) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (ModuleInfo module : new ArrayList<>(report.getBusinessModules())) {
                for (DependencyNode node : new ArrayList<>(module.getClasses())) {
                    ClassAssignment assignment = report.getAssignment(node.getId());
                    if (assignment != null && assignment.locked()) {
                        continue;
                    }
                    Set<String> subtypeModules = new java.util.TreeSet<>();
                    for (DependencyEdge edge : graph.getIncomingEdges(node)) {
                        if (SUBTYPING.contains(edge.getDependencyType())) {
                            String owner = report.moduleOf(edge.getSource().getId());
                            ModuleInfo ownerModule = owner == null ? null : report.getModule(owner);
                            if (ownerModule != null && ownerModule.isBusinessModule()) {
                                subtypeModules.add(owner);
                            }
                        }
                    }
                    subtypeModules.add(module.getModuleName());
                    long others = subtypeModules.stream().filter(m -> !m.equals(module.getModuleName())).count();
                    if (others < 2) {
                        continue; // a subtype in one other module is an ordinary cross-module dependency
                    }
                    if (dependsOnBusinessModule(report, graph, node)) {
                        report.getWarnings().add(node.getClassName() + " is a base type for modules " + subtypeModules
                                + " but depends on business code; it was left in '" + module.getModuleName() + "'.");
                        continue;
                    }
                    report.assign(node, ModuleDiscoveryReport.SHARED, ModuleCategory.SHARED, 0.8,
                            ClassAssignment.Origin.AUTOMATIC,
                            List.of("base type extended or implemented by modules " + subtypeModules));
                    changed = true;
                }
            }
            report.pruneEmptyModules();
        }
    }

    private boolean dependsOnBusinessModule(ModuleDiscoveryReport report, DependencyGraph graph, DependencyNode node) {
        String own = report.moduleOf(node.getId());
        for (DependencyNode target : graph.getSuccessors(node)) {
            String owner = report.moduleOf(target.getId());
            ModuleInfo targetModule = owner == null ? null : report.getModule(owner);
            if (targetModule != null && targetModule.isBusinessModule() && !owner.equals(own)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDependencies(DependencyGraph graph, ModuleInfo module) {
        return module.getClasses().stream().anyMatch(n -> !graph.getOutgoingEdges(n).isEmpty() || !graph.getIncomingEdges(n).isEmpty());
    }

    private boolean anyLocked(ModuleDiscoveryReport report, ModuleInfo module) {
        return module.getClasses().stream().anyMatch(n -> {
            ClassAssignment a = report.getAssignment(n.getId());
            return a != null && a.locked();
        });
    }

    private void promoteShared(ModuleDiscoveryReport report, DependencyGraph graph, ArchMorphProperties.ModuleDiscovery config) {
        for (ModuleInfo module : new ArrayList<>(report.getBusinessModules())) {
            for (DependencyNode node : new ArrayList<>(module.getClasses())) {
                ClassAssignment assignment = report.getAssignment(node.getId());
                if (assignment != null && assignment.locked()) {
                    continue;
                }
                if (!DomainTerms.isGenericName(node.getClassName())) {
                    continue;
                }
                Set<String> users = new HashSet<>();
                for (DependencyNode caller : graph.getPredecessors(node)) {
                    String owner = report.moduleOf(caller.getId());
                    ModuleInfo ownerModule = owner == null ? null : report.getModule(owner);
                    if (ownerModule != null && ownerModule.isBusinessModule()) {
                        users.add(owner);
                    }
                }
                if (users.size() >= config.getSharedUsageThreshold()) {
                    report.assign(node, ModuleDiscoveryReport.SHARED, ModuleCategory.SHARED, 0.85,
                            ClassAssignment.Origin.AUTOMATIC,
                            List.of("generic class used by " + users.size() + " modules " + new java.util.TreeSet<>(users)));
                }
            }
        }
    }

    /**
     * An implementation of a module's interface ({@code MyBatisCommentRepository implements CommentRepository},
     * {@code DefaultJwtService implements JwtService}) is that module's adapter: it belongs with the port it
     * implements, not in shared (a shared → module dependency) or at the application root. Applies only when
     * every implemented project type belongs to the same business module.
     */
    private void moveAdaptersToTheirPort(ModuleDiscoveryReport report, DependencyGraph graph) {
        ModuleInfo shared = report.getModule(ModuleDiscoveryReport.SHARED);
        if (shared == null) {
            return;
        }
        for (DependencyNode node : new ArrayList<>(shared.getClasses())) {
            ClassAssignment assignment = report.getAssignment(node.getId());
            if (assignment != null && assignment.locked()) {
                continue;
            }
            Set<String> portModules = new java.util.TreeSet<>();
            Set<String> ports = new java.util.TreeSet<>();
            for (var edge : graph.getOutgoingEdges(node)) {
                if (!SUBTYPING.contains(edge.getDependencyType())) {
                    continue;
                }
                String owner = report.moduleOf(edge.getTarget().getId());
                ModuleInfo module = owner == null ? null : report.getModule(owner);
                portModules.add(module != null && module.isBusinessModule() ? owner : "");
                ports.add(edge.getTarget().getClassName());
            }
            if (portModules.size() != 1 || portModules.contains("")) {
                continue;
            }
            String target = portModules.iterator().next();
            report.assign(node, target, ModuleCategory.BUSINESS_MODULE, 0.8, ClassAssignment.Origin.AUTOMATIC,
                    List.of("implements " + String.join(", ", ports) + " of module '" + target
                            + "': an adapter belongs with the port it implements"));
        }
        report.pruneEmptyModules();
    }

    /**
     * Shared code that depends on business modules but is used by none of them is application wiring — a
     * global {@code @RestControllerAdvice} handling module exceptions, a security configuration that uses a
     * module's {@code UserDetailsService}. In shared it would create a shared ↔ module cycle; at the
     * application root (which belongs to no module) it can see every module without one. Shared code that
     * modules also use is left in shared and reported, because that cycle needs a design decision.
     */
    private void moveCompositionRoots(ModuleDiscoveryReport report, DependencyGraph graph) {
        ModuleInfo shared = report.getModule(ModuleDiscoveryReport.SHARED);
        if (shared == null) {
            return;
        }
        for (DependencyNode node : new ArrayList<>(shared.getClasses())) {
            ClassAssignment assignment = report.getAssignment(node.getId());
            if (assignment != null && assignment.locked()) {
                continue;
            }
            Set<String> uses = new java.util.TreeSet<>();
            for (DependencyNode target : graph.getSuccessors(node)) {
                ModuleInfo module = report.getModule(report.moduleOf(target.getId()) == null ? "" : report.moduleOf(target.getId()));
                if (module != null && module.isBusinessModule()) {
                    uses.add(module.getModuleName());
                }
            }
            if (uses.isEmpty()) {
                continue;
            }
            boolean usedByModules = graph.getPredecessors(node).stream().anyMatch(caller -> {
                ModuleInfo module = report.getModule(report.moduleOf(caller.getId()) == null ? "" : report.moduleOf(caller.getId()));
                return module != null && (module.isBusinessModule() || module.getCategory() == ModuleCategory.SHARED);
            });
            if (usedByModules) {
                report.getWarnings().add("Shared class " + node.getClassName() + " depends on modules " + uses
                        + " and is used by other code; this is a dependency cycle to resolve by design (an interface or event in shared).");
                continue;
            }
            if (uses.size() == 1 && node.getComponentType() == ComponentType.CONTROLLER) {
                // an endpoint the framework calls (e.g. a GraphQL "me" query) that serves exactly one module
                String target = uses.iterator().next();
                report.assign(node, target, ModuleCategory.BUSINESS_MODULE, 0.7, ClassAssignment.Origin.AUTOMATIC,
                        List.of("endpoint that only uses module '" + target + "'"));
                continue;
            }
            report.assign(node, ModuleDiscoveryReport.APPLICATION, ModuleCategory.APPLICATION, 0.8,
                    ClassAssignment.Origin.AUTOMATIC,
                    List.of("application wiring: depends on modules " + uses + " and no module depends on it; "
                            + "placed at the application root to avoid a shared ↔ module cycle"));
        }
        report.pruneEmptyModules();
    }

    /** Invariant check: a class must be in exactly one module. Repairs duplicates deterministically. */
    private void enforceSingleOwnership(ModuleDiscoveryReport report) {
        Set<String> seen = new HashSet<>();
        for (ModuleInfo module : new ArrayList<>(report.getModules())) {
            for (DependencyNode node : new ArrayList<>(module.getClasses())) {
                if (!seen.add(node.getId())) {
                    module.getClasses().remove(node);
                    report.getWarnings().add("Class " + node.getClassName() + " was listed in more than one module; kept the first.");
                }
            }
        }
    }

    private void addWarnings(ModuleDiscoveryReport report, ArchMorphProperties.ModuleDiscovery config) {
        int businessClasses = report.getBusinessModules().stream().mapToInt(ModuleInfo::getClassCount).sum();
        for (ModuleInfo module : report.getBusinessModules()) {
            module.getWarnings().removeIf(w -> w.startsWith("[opt]"));
            if (module.getClassCount() == 1) {
                String hint = module.getDependenciesOnModules().entrySet().stream()
                        .filter(e -> report.getModule(e.getKey()) != null && report.getModule(e.getKey()).isBusinessModule())
                        .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                        .map(e -> "; it mostly uses '" + e.getKey() + "' — merge it there unless it is a domain of its own")
                        .orElse("");
                module.getWarnings().add("[opt] suspicious singleton module: only one class (" + only(module) + ")" + hint);
            }
            if (module.getClassCount() >= config.getOversizedModuleMinClasses()
                    && businessClasses > 0
                    && (double) module.getClassCount() / businessClasses > config.getOversizedModuleFraction()) {
                module.getWarnings().add("[opt] oversized module: " + module.getClassCount() + " of " + businessClasses
                        + " business classes; consider splitting it");
            }
            if (module.getClassCount() >= 3 && module.getCohesion() < config.getLowCohesionThreshold()) {
                module.getWarnings().add(String.format("[opt] low cohesion (%.2f): most dependencies cross the module boundary", module.getCohesion()));
            }
            Set<String> targets = new java.util.TreeSet<>(module.getDependenciesOnModules().keySet());
            targets.removeIf(t -> report.getModule(t) == null || !report.getModule(t).isBusinessModule());
            if (targets.size() >= 3 && module.getCohesion() < config.getLowCohesionThreshold()) {
                module.getWarnings().add("[opt] looks like a facade over modules " + targets
                        + "; in a modular monolith consider splitting it so each module owns its part");
            }
            if (module.getExternalCoupling() > config.getHighCouplingThreshold()) {
                module.getWarnings().add(String.format("[opt] highly coupled to other modules (%.2f); automatic transformation is safe but review the boundaries", module.getExternalCoupling()));
            }
        }
        if (report.getBusinessModuleCount() == 1 && businessClasses > 10) {
            report.getWarnings().add("Only one business module was discovered; the project may be a single domain or its naming may not reveal domains.");
        }
    }

    private String only(ModuleInfo module) {
        return module.getClasses().iterator().next().getClassName();
    }
}
