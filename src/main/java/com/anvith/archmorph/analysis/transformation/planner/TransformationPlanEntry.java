package com.anvith.archmorph.analysis.transformation.planner;

import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.transformation.FolderType;
import com.anvith.archmorph.analysis.transformation.RiskLevel;
import com.anvith.archmorph.analysis.transformation.SafetyLevel;
import com.anvith.archmorph.analysis.transformation.TransformationAction;
import com.anvith.archmorph.analysis.transformation.mapping.TransformationMapping;
import com.anvith.archmorph.parser.SourceScope;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One executable step: a source file, where it ends up, and what happens to it.
 * All paths are project-relative; absolute server paths never appear in a plan.
 */
public class TransformationPlanEntry {

    private String id;

    private SourceScope scope = SourceScope.MAIN;

    /** Primary class of the file (null for files without a type). */
    private DependencyNode node;

    private TransformationMapping mapping;

    private Path sourceFile;

    private Path targetFile;

    private String sourcePackage;

    private String targetPackage;

    private String module;

    private FolderType folder;

    private final Set<TransformationAction> actions = EnumSet.noneOf(TransformationAction.class);

    private SafetyLevel safety = SafetyLevel.SAFE;

    private RiskLevel risk = RiskLevel.LOW;

    private double confidence = 1.0;

    /** Types declared in the file (top-level and nested) with old and new qualified names. */
    private final List<ClassMove> classes = new ArrayList<>();

    /** Why the file has this safety level or action. */
    private final List<String> reasons = new ArrayList<>();

    /** Human-readable rewrite requirements, e.g. "rewrite package", "update 3 imports". */
    private final List<String> rewriteRequirements = new ArrayList<>();

    public boolean isMoved() {
        return actions.contains(TransformationAction.MOVE);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public SourceScope getScope() {
        return scope;
    }

    public void setScope(SourceScope scope) {
        this.scope = scope;
    }

    public DependencyNode getNode() {
        return node;
    }

    public void setNode(DependencyNode node) {
        this.node = node;
    }

    public TransformationMapping getMapping() {
        return mapping;
    }

    public void setMapping(TransformationMapping mapping) {
        this.mapping = mapping;
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(Path sourceFile) {
        this.sourceFile = sourceFile;
    }

    public Path getTargetFile() {
        return targetFile;
    }

    public void setTargetFile(Path targetFile) {
        this.targetFile = targetFile;
    }

    public String getSourcePackage() {
        return sourcePackage;
    }

    public void setSourcePackage(String sourcePackage) {
        this.sourcePackage = sourcePackage;
    }

    public String getTargetPackage() {
        return targetPackage;
    }

    public void setTargetPackage(String targetPackage) {
        this.targetPackage = targetPackage;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public FolderType getFolder() {
        return folder;
    }

    public void setFolder(FolderType folder) {
        this.folder = folder;
    }

    public Set<TransformationAction> getActions() {
        return actions;
    }

    public SafetyLevel getSafety() {
        return safety;
    }

    public void setSafety(SafetyLevel safety) {
        this.safety = safety;
        this.risk = safety.toRisk();
    }

    public RiskLevel getRisk() {
        return risk;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<ClassMove> getClasses() {
        return classes;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public List<String> getRewriteRequirements() {
        return rewriteRequirements;
    }

    @Override
    public String toString() {
        return sourceFile + " -> " + targetFile + " " + actions + " [" + safety + "]";
    }
}
