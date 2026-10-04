package com.anvith.archmorph.analysis.module;

import com.anvith.archmorph.analysis.dependency.DependencyEdge;
import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.module.naming.DomainTerms;
import com.anvith.archmorph.parser.ComponentType;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

/**
 * Computes the static-analysis indicators of each module: dependency counts,
 * cohesion, external coupling, confidence and evidence. The numbers describe
 * the dependency graph; they are not a verdict on design quality.
 *
 * <pre>
 * cohesion         = internal / (internal + crossing)         crossing = edges to or from other modules
 * externalCoupling = edges to other BUSINESS modules / all edges touching the module
 * confidence       = 0.35·cohesion + 0.30·nameAgreement + 0.20·stackCompleteness + 0.15·(1 − externalCoupling)
 * </pre>
 */
@Service
public class ModuleMetricsCalculator {

    private static final Set<ComponentType> STACK = EnumSet.of(
            ComponentType.CONTROLLER, ComponentType.SERVICE, ComponentType.REPOSITORY, ComponentType.ENTITY);

    public void calculate(ModuleDiscoveryReport report, DependencyGraph graph) {
        for (ModuleInfo module : report.getModules()) {
            module.getDependenciesOnModules().clear();
            module.getEvidence().removeIf(e -> e.startsWith("[metric]"));
            module.getWarnings().removeIf(w -> w.startsWith("[metric]"));

            int internal = 0;
            int outgoingExternal = 0;
            int outgoingToBusiness = 0;
            int incomingExternal = 0;
            for (DependencyNode node : module.getClasses()) {
                for (DependencyEdge edge : graph.getOutgoingEdges(node)) {
                    String targetModule = report.moduleOf(edge.getTarget().getId());
                    if (targetModule == null) {
                        continue;
                    }
                    if (targetModule.equals(module.getModuleName())) {
                        internal++;
                    } else {
                        outgoingExternal++;
                        module.getDependenciesOnModules().merge(targetModule, 1, Integer::sum);
                        ModuleInfo target = report.getModule(targetModule);
                        if (target != null && target.isBusinessModule()) {
                            outgoingToBusiness++;
                        }
                    }
                }
                for (DependencyEdge edge : graph.getIncomingEdges(node)) {
                    String sourceModule = report.moduleOf(edge.getSource().getId());
                    if (sourceModule != null && !sourceModule.equals(module.getModuleName())) {
                        incomingExternal++;
                    }
                }
            }

            module.setInternalDependencies(internal);
            module.setExternalDependencies(outgoingExternal);

            if (!module.isBusinessModule()) {
                module.setCohesion(0);
                module.setExternalCoupling(0);
                module.setConfidence(1.0);
                continue;
            }

            int crossing = outgoingExternal + incomingExternal;
            double cohesion = internal + crossing == 0 ? 1.0 : (double) internal / (internal + crossing);
            int touching = internal + outgoingExternal + incomingExternal;
            int businessCrossing = outgoingToBusiness + countIncomingFromBusiness(module, report, graph);
            double coupling = touching == 0 ? 0 : (double) businessCrossing / touching;

            double nameAgreement = nameAgreement(module);
            double stack = stackCompleteness(module);
            double confidence = 0.35 * cohesion + 0.30 * nameAgreement + 0.20 * stack + 0.15 * (1 - coupling);

            module.setCohesion(round(cohesion));
            module.setExternalCoupling(round(coupling));
            module.setConfidence(round(Math.max(0.05, Math.min(0.99, confidence))));
            describe(module, nameAgreement, stack);
        }
    }

    private int countIncomingFromBusiness(ModuleInfo module, ModuleDiscoveryReport report, DependencyGraph graph) {
        int count = 0;
        for (DependencyNode node : module.getClasses()) {
            for (DependencyEdge edge : graph.getIncomingEdges(node)) {
                String sourceModule = report.moduleOf(edge.getSource().getId());
                ModuleInfo source = sourceModule == null ? null : report.getModule(sourceModule);
                if (source != null && source.isBusinessModule() && !sourceModule.equals(module.getModuleName())) {
                    count++;
                }
            }
        }
        return count;
    }

    private double nameAgreement(ModuleInfo module) {
        if (module.getClasses().isEmpty()) {
            return 0;
        }
        long agreeing = module.getClasses().stream()
                .filter(n -> DomainTerms.stem(n.getClassName()).startsWith(module.getModuleName()))
                .count();
        return (double) agreeing / module.getClasses().size();
    }

    private double stackCompleteness(ModuleInfo module) {
        Set<ComponentType> present = EnumSet.noneOf(ComponentType.class);
        module.getClasses().forEach(n -> {
            if (n.getComponentType() != null && STACK.contains(n.getComponentType())) {
                present.add(n.getComponentType());
            }
        });
        return (double) present.size() / STACK.size();
    }

    private void describe(ModuleInfo module, double nameAgreement, double stack) {
        module.getEvidence().add(String.format("[metric] %d of %d classes share the domain term '%s'",
                Math.round(nameAgreement * module.getClassCount()), module.getClassCount(), module.getModuleName()));
        Set<ComponentType> present = EnumSet.noneOf(ComponentType.class);
        module.getClasses().forEach(n -> {
            if (n.getComponentType() != null) {
                present.add(n.getComponentType());
            }
        });
        module.getEvidence().add("[metric] roles present: " + present);
        module.getEvidence().add(String.format("[metric] %d internal and %d outgoing external dependencies; cohesion %.2f",
                module.getInternalDependencies(), module.getExternalDependencies(), module.getCohesion()));
        if (stack < 0.5) {
            module.getWarnings().add("[metric] module lacks most of the controller/service/repository/entity stack");
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
