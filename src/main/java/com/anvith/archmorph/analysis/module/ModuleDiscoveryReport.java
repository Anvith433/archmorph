package com.anvith.archmorph.analysis.module;

import com.anvith.archmorph.analysis.dependency.DependencyNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modules plus the per-class assignment decisions.
 *
 * <p>Invariant: every top-level class belongs to exactly one module. The
 * only way to place a class is {@link #assign}, which removes it from any
 * previous module — classes can never be silently duplicated.</p>
 */
public class ModuleDiscoveryReport {

    public static final String SHARED = "shared";
    public static final String APPLICATION = "application";

    private final Map<String, ModuleInfo> modules = new LinkedHashMap<>();

    private final Map<String, ClassAssignment> assignments = new LinkedHashMap<>();

    private final List<String> warnings = new ArrayList<>();

    /** Names under which the pipeline cannot place business modules. */
    public static boolean isReservedName(String name) {
        return SHARED.equals(name) || APPLICATION.equals(name);
    }

    public ModuleInfo getOrCreateModule(String moduleName) {
        return modules.computeIfAbsent(moduleName, ModuleInfo::new);
    }

    public ModuleInfo getOrCreateModule(String moduleName, ModuleCategory category) {
        return modules.computeIfAbsent(moduleName, name -> new ModuleInfo(name, category));
    }

    public ModuleInfo getModule(String moduleName) {
        return modules.get(moduleName);
    }

    public Collection<ModuleInfo> getModules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    public List<ModuleInfo> getBusinessModules() {
        return modules.values().stream().filter(ModuleInfo::isBusinessModule).toList();
    }

    public int getModuleCount() {
        return modules.size();
    }

    public int getBusinessModuleCount() {
        return (int) modules.values().stream().filter(ModuleInfo::isBusinessModule).count();
    }

    public boolean isEmpty() {
        return modules.isEmpty();
    }

    public void removeModule(String moduleName) {
        ModuleInfo removed = modules.remove(moduleName);
        if (removed != null) {
            removed.getClasses().forEach(n -> assignments.remove(n.getId()));
        }
    }

    /** Rename a module, keeping its classes and assignments. */
    public void renameModule(String oldName, String newName) {
        ModuleInfo module = modules.remove(oldName);
        if (module == null) {
            return;
        }
        module.setModuleName(newName);
        modules.put(newName, module);
        assignments.replaceAll((k, a) -> a.moduleName().equals(oldName)
                ? new ClassAssignment(a.qualifiedName(), newName, a.category(), a.confidence(), a.origin(),
                a.locked(), a.excluded(), a.reasons())
                : a);
    }

    /**
     * Place a class into a module (creating it if needed), removing it from any other module.
     * The category decides the module kind for newly created modules.
     */
    public void assign(DependencyNode node, String moduleName, ModuleCategory category, double confidence,
                       ClassAssignment.Origin origin, List<String> reasons) {
        ClassAssignment previous = assignments.get(node.getId());
        modules.values().forEach(m -> m.getClasses().remove(node));
        ModuleCategory moduleKind = SHARED.equals(moduleName) ? ModuleCategory.SHARED
                : APPLICATION.equals(moduleName) ? ModuleCategory.APPLICATION : ModuleCategory.BUSINESS_MODULE;
        getOrCreateModule(moduleName, moduleKind).addClass(node);
        ClassAssignment assignment = new ClassAssignment(node.getId(), moduleName, category, confidence, origin,
                previous != null && previous.locked(), previous != null && previous.excluded(), List.copyOf(reasons));
        assignments.put(node.getId(), assignment);
    }

    public void putAssignment(ClassAssignment assignment) {
        assignments.put(assignment.qualifiedName(), assignment);
    }

    public ClassAssignment getAssignment(String qualifiedName) {
        return assignments.get(qualifiedName);
    }

    public Map<String, ClassAssignment> getAssignments() {
        return Collections.unmodifiableMap(assignments);
    }

    /** Name of the module that owns the class, or null. */
    public String moduleOf(String qualifiedName) {
        ClassAssignment assignment = assignments.get(qualifiedName);
        return assignment == null ? null : assignment.moduleName();
    }

    public List<String> getWarnings() {
        return warnings;
    }

    /** Remove modules that lost all classes. */
    public void pruneEmptyModules() {
        modules.values().removeIf(ModuleInfo::isEmpty);
    }

    /** Independent copy (modules, assignments, warnings); nodes are shared (immutable for this purpose). */
    public ModuleDiscoveryReport copy() {
        ModuleDiscoveryReport copy = new ModuleDiscoveryReport();
        for (ModuleInfo module : modules.values()) {
            ModuleInfo clone = new ModuleInfo(module.getModuleName(), module.getCategory());
            module.getClasses().forEach(clone::addClass);
            clone.setConfidence(module.getConfidence());
            clone.setCohesion(module.getCohesion());
            clone.setExternalCoupling(module.getExternalCoupling());
            clone.setInternalDependencies(module.getInternalDependencies());
            clone.setExternalDependencies(module.getExternalDependencies());
            clone.getDependenciesOnModules().putAll(module.getDependenciesOnModules());
            clone.getEvidence().addAll(module.getEvidence());
            clone.getWarnings().addAll(module.getWarnings());
            copy.modules.put(clone.getModuleName(), clone);
        }
        copy.assignments.putAll(assignments);
        copy.warnings.addAll(warnings);
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("Modules: ").append(modules.size()).append('\n');
        modules.values().forEach(builder::append);
        return builder.toString();
    }
}
