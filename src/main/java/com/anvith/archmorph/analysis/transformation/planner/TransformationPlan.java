package com.anvith.archmorph.analysis.transformation.planner;

import com.anvith.archmorph.analysis.transformation.SafetyLevel;
import com.anvith.archmorph.analysis.transformation.target.TargetStrategy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The complete, deterministic plan. Planning never mutates files; the executor
 * only carries out what is written here.
 */
public class TransformationPlan {

    private final List<TransformationPlanEntry> entries = new ArrayList<>();

    private final List<PlanConflict> conflicts = new ArrayList<>();

    private final List<String> warnings = new ArrayList<>();

    private final List<ResourceFinding> resourceFindings = new ArrayList<>();

    /** Every moved qualified name (top-level and nested) to its new qualified name. */
    private final Map<String, String> classMap = new LinkedHashMap<>();

    private TargetStrategy strategy = TargetStrategy.MODULAR_BY_DOMAIN;

    private String basePackage = "";

    public void addEntry(TransformationPlanEntry entry) {
        entries.add(entry);
    }

    public List<TransformationPlanEntry> getEntries() {
        return entries;
    }

    public List<PlanConflict> getConflicts() {
        return conflicts;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<ResourceFinding> getResourceFindings() {
        return resourceFindings;
    }

    public Map<String, String> getClassMap() {
        return classMap;
    }

    public TargetStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(TargetStrategy strategy) {
        this.strategy = strategy;
    }

    public String getBasePackage() {
        return basePackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    public Map<SafetyLevel, Long> safetyCounts() {
        Map<SafetyLevel, Long> counts = new EnumMap<>(SafetyLevel.class);
        for (SafetyLevel level : SafetyLevel.values()) {
            counts.put(level, entries.stream().filter(e -> e.getSafety() == level).count());
        }
        return Collections.unmodifiableMap(counts);
    }

    public long movedCount() {
        return entries.stream().filter(TransformationPlanEntry::isMoved).count();
    }

    public boolean requiresManualReview() {
        return entries.stream().anyMatch(e -> !e.getSafety().allowsAutomaticMove()) || !conflicts.isEmpty();
    }

    /** SHA-256 over the canonical content of the plan; equal inputs must give equal fingerprints. */
    public String fingerprint() {
        StringBuilder canonical = new StringBuilder();
        canonical.append(strategy).append('|').append(basePackage).append('\n');
        for (TransformationPlanEntry e : entries) {
            canonical.append(e.getScope()).append('|').append(e.getSourceFile()).append('|').append(e.getTargetFile())
                    .append('|').append(e.getActions()).append('|').append(e.getSafety()).append('|').append(e.getModule())
                    .append('\n');
        }
        classMap.forEach((k, v) -> canonical.append(k).append("->").append(v).append('\n'));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
