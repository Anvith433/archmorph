package com.anvith.archmorph.analysis.module.optimizer;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
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
 *   <li><b>Merge fragments</b>: a module without controller/service/repository and with at most three
 *       classes (typically only a value entity or DTOs) is merged into the module that owns the
 *       majority of its dependencies.</li>
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

    private static final Set<ComponentType> BEHAVIOUR = EnumSet.of(
            ComponentType.CONTROLLER, ComponentType.SERVICE, ComponentType.REPOSITORY);

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

        mergeFragments(report, graph);
        promoteShared(report, graph, config);
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
                if (fragment.getClassCount() > 3 || hasBehaviour(fragment) || anyLocked(report, fragment)) {
                    continue;
                }
                Map<String, Integer> weights = dependencyWeightsByModule(report, graph, fragment);
                int total = weights.values().stream().mapToInt(Integer::intValue).sum();
                if (total == 0) {
                    continue;
                }
                Map.Entry<String, Integer> best = weights.entrySet().stream()
                        .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                        .orElseThrow();
                ModuleInfo target = report.getModule(best.getKey());
                if (target == null || !target.isBusinessModule() || best.getValue() < 0.6 * total) {
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

    private boolean hasBehaviour(ModuleInfo module) {
        return module.getClasses().stream().anyMatch(n -> n.getComponentType() != null && BEHAVIOUR.contains(n.getComponentType()));
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
                module.getWarnings().add("[opt] suspicious singleton module: only one class (" + only(module) + ")");
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
