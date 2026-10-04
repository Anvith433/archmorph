package com.anvith.archmorph.analysis.transformation;

/**
 * How safe the planned change of a file is. ArchMorph never hides uncertainty.
 *
 * <ul>
 *   <li>{@code SAFE} – deterministic package move and rewrite</li>
 *   <li>{@code SAFE_WITH_WARNING} – performed automatically; review recommended</li>
 *   <li>{@code MANUAL_REVIEW} – not performed automatically (file stays in place)</li>
 *   <li>{@code UNSUPPORTED} – cannot be transformed (file stays in place)</li>
 * </ul>
 */
public enum SafetyLevel {
    SAFE,
    SAFE_WITH_WARNING,
    MANUAL_REVIEW,
    UNSUPPORTED;

    public boolean allowsAutomaticMove() {
        return this == SAFE || this == SAFE_WITH_WARNING;
    }

    public RiskLevel toRisk() {
        return switch (this) {
            case SAFE -> RiskLevel.LOW;
            case SAFE_WITH_WARNING -> RiskLevel.MEDIUM;
            case MANUAL_REVIEW, UNSUPPORTED -> RiskLevel.HIGH;
        };
    }
}
