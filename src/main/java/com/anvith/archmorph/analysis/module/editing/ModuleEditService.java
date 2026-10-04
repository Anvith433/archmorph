package com.anvith.archmorph.analysis.module.editing;

import com.anvith.archmorph.analysis.dependency.DependencyGraph;
import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.module.ClassAssignment;
import com.anvith.archmorph.analysis.module.Exposure;
import com.anvith.archmorph.analysis.module.ModuleCategory;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.module.ModuleInfo;
import com.anvith.archmorph.analysis.module.ModuleMetricsCalculator;
import com.anvith.archmorph.analysis.module.naming.DefaultModuleNamingStrategy;
import com.anvith.archmorph.common.exception.ArchMorphException;
import com.anvith.archmorph.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Applies user decisions to a module suggestion. The suggestion itself is never modified;
 * a new report is returned. Every operation validates its input and fails with
 * {@code INVALID_MODULE_OPERATION} instead of leaving the plan in a half-edited state.
 */
@Service
public class ModuleEditService {

    private final ModuleMetricsCalculator metricsCalculator;

    public ModuleEditService(ModuleMetricsCalculator metricsCalculator) {
        this.metricsCalculator = metricsCalculator;
    }

    public ModuleDiscoveryReport apply(ModuleDiscoveryReport suggestion, List<ModuleEdit> edits, DependencyGraph graph) {
        ModuleDiscoveryReport report = suggestion.copy();
        for (ModuleEdit edit : edits == null ? List.<ModuleEdit>of() : edits) {
            if (edit == null || edit.type() == null) {
                throw invalid("Missing operation type.");
            }
            switch (edit.type()) {
                case RENAME_MODULE -> rename(report, edit);
                case MERGE_MODULES -> merge(report, edit);
                case SPLIT_MODULE -> split(report, edit, graph);
                case MOVE_CLASS -> move(report, edit, graph);
                case MOVE_TO_SHARED -> moveToShared(report, edit, graph);
                case EXCLUDE_CLASS -> setExcluded(report, edit, true);
                case INCLUDE_CLASS -> setExcluded(report, edit, false);
                case LOCK_CLASS -> setLocked(report, edit, true);
                case UNLOCK_CLASS -> setLocked(report, edit, false);
                case EXPOSE_CLASS -> report.getExposureOverrides().put(assignment(report, edit.className()).qualifiedName(), Exposure.PUBLIC_API);
                case INTERNAL_CLASS -> report.getExposureOverrides().put(assignment(report, edit.className()).qualifiedName(), Exposure.INTERNAL);
                case AUTO_EXPOSURE -> report.getExposureOverrides().remove(assignment(report, edit.className()).qualifiedName());
            }
        }
        report.pruneEmptyModules();
        requireInternalClassesUnused(report, graph);
        metricsCalculator.calculate(report, graph);
        return report;
    }

    private void rename(ModuleDiscoveryReport report, ModuleEdit edit) {
        ModuleInfo module = businessModule(report, edit.module());
        String newName = validName(edit.newName());
        if (!newName.equals(module.getModuleName()) && report.getModule(newName) != null) {
            throw invalid("A module named '" + newName + "' already exists.");
        }
        report.renameModule(module.getModuleName(), newName);
    }

    private void merge(ModuleDiscoveryReport report, ModuleEdit edit) {
        if (edit.sources() == null || edit.sources().isEmpty()) {
            throw invalid("Select at least one module to merge.");
        }
        ModuleInfo target = businessModule(report, edit.target());
        for (String sourceName : edit.sources()) {
            if (sourceName.equals(target.getModuleName())) {
                continue;
            }
            ModuleInfo source = businessModule(report, sourceName);
            for (DependencyNode node : new ArrayList<>(source.getClasses())) {
                requireNotLocked(report, node.getId());
                moveNode(report, node, target.getModuleName(), ModuleCategory.BUSINESS_MODULE,
                        "merged module '" + sourceName + "' into '" + target.getModuleName() + "'");
            }
        }
    }

    private void split(ModuleDiscoveryReport report, ModuleEdit edit, DependencyGraph graph) {
        ModuleInfo source = businessModule(report, edit.module());
        String newName = validName(edit.newName());
        if (report.getModule(newName) != null) {
            throw invalid("A module named '" + newName + "' already exists.");
        }
        if (edit.classes() == null || edit.classes().isEmpty()) {
            throw invalid("Select the classes to move into the new module.");
        }
        if (edit.classes().size() >= source.getClassCount()) {
            throw invalid("Splitting must leave at least one class in the original module.");
        }
        for (String className : edit.classes()) {
            DependencyNode node = nodeIn(source, className);
            requireNotLocked(report, node.getId());
            moveNode(report, node, newName, ModuleCategory.BUSINESS_MODULE,
                    "split from module '" + source.getModuleName() + "'");
        }
    }

    private void move(ModuleDiscoveryReport report, ModuleEdit edit, DependencyGraph graph) {
        DependencyNode node = findNode(report, graph, edit.className());
        requireNotLocked(report, node.getId());
        String target = edit.target();
        if (ModuleDiscoveryReport.SHARED.equals(target)) {
            moveToShared(report, edit, graph);
            return;
        }
        if (target == null || ModuleDiscoveryReport.isReservedName(target)) {
            throw invalid("Choose a business module as the target.");
        }
        validName(target);
        moveNode(report, node, target, ModuleCategory.BUSINESS_MODULE, "moved by user to module '" + target + "'");
    }

    private void moveToShared(ModuleDiscoveryReport report, ModuleEdit edit, DependencyGraph graph) {
        DependencyNode node = findNode(report, graph, edit.className());
        requireNotLocked(report, node.getId());
        moveNode(report, node, ModuleDiscoveryReport.SHARED, ModuleCategory.SHARED, "moved to shared by user");
    }

    private void setExcluded(ModuleDiscoveryReport report, ModuleEdit edit, boolean excluded) {
        ClassAssignment assignment = assignment(report, edit.className());
        report.putAssignment(assignment.withExcluded(excluded));
    }

    private void setLocked(ModuleDiscoveryReport report, ModuleEdit edit, boolean locked) {
        ClassAssignment assignment = assignment(report, edit.className());
        report.putAssignment(assignment.withLocked(locked));
    }

    /** A class marked internal must not be used by another module once all decisions are applied. */
    private void requireInternalClassesUnused(ModuleDiscoveryReport report, DependencyGraph graph) {
        for (Map.Entry<String, Exposure> override : report.getExposureOverrides().entrySet()) {
            if (override.getValue() != Exposure.INTERNAL) {
                continue;
            }
            DependencyNode node = graph.findNode(override.getKey());
            String own = report.moduleOf(override.getKey());
            if (node == null || own == null) {
                continue;
            }
            java.util.Set<String> users = new java.util.TreeSet<>();
            for (DependencyNode caller : graph.getPredecessors(node)) {
                String module = report.moduleOf(caller.getId());
                if (module != null && !module.equals(own) && !ModuleDiscoveryReport.APPLICATION.equals(module)) {
                    users.add(caller.getClassName() + " (" + module + ")");
                }
            }
            if (!users.isEmpty()) {
                throw invalid(node.getClassName() + " cannot be internal: it is used by " + String.join(", ", users)
                        + ". Move those classes or keep it in the module's public API.");
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private void moveNode(ModuleDiscoveryReport report, DependencyNode node, String module, ModuleCategory category, String reason) {
        report.assign(node, module, category, 1.0, ClassAssignment.Origin.USER, List.of(reason));
    }

    private ModuleInfo businessModule(ModuleDiscoveryReport report, String name) {
        ModuleInfo module = name == null ? null : report.getModule(name);
        if (module == null || !module.isBusinessModule()) {
            throw invalid("Module '" + safe(name) + "' does not exist.");
        }
        return module;
    }

    private DependencyNode nodeIn(ModuleInfo module, String className) {
        return module.getClasses().stream()
                .filter(n -> n.getId().equals(className))
                .findFirst()
                .orElseThrow(() -> invalid("Class '" + safe(className) + "' is not part of module '" + module.getModuleName() + "'."));
    }

    private DependencyNode findNode(ModuleDiscoveryReport report, DependencyGraph graph, String className) {
        assignment(report, className);
        DependencyNode node = graph.findNode(className);
        if (node == null) {
            throw invalid("Unknown class '" + safe(className) + "'.");
        }
        return node;
    }

    private ClassAssignment assignment(ModuleDiscoveryReport report, String className) {
        ClassAssignment assignment = className == null ? null : report.getAssignment(className);
        if (assignment == null) {
            throw invalid("Unknown class '" + safe(className) + "'.");
        }
        return assignment;
    }

    private void requireNotLocked(ModuleDiscoveryReport report, String className) {
        ClassAssignment assignment = report.getAssignment(className);
        if (assignment != null && assignment.locked()) {
            throw invalid("Class '" + simple(className) + "' is locked. Unlock it before moving it.");
        }
    }

    private String validName(String name) {
        if (!DefaultModuleNamingStrategy.isValidModuleName(name) || DefaultModuleNamingStrategy.RESERVED.contains(name)) {
            throw invalid("Module names must be lower-case Java identifiers (letters and digits, starting with a letter) "
                    + "and must not be a reserved name such as 'shared' or 'config'.");
        }
        return name;
    }

    private static String simple(String qualified) {
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }

    /** Never echo unbounded user input back. */
    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replaceAll("[^A-Za-z0-9_.$-]", "?");
        return clean.length() > 80 ? clean.substring(0, 80) : clean;
    }

    private static ArchMorphException invalid(String message) {
        return new ArchMorphException(ErrorCode.INVALID_MODULE_OPERATION, message);
    }
}
