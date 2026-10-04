package com.anvith.archmorph.analysis.module.affinity;

/**
 * Explainable affinity between two classes. Every component is in [0, 1];
 * {@code total} is the weighted sum minus the cross-domain penalty, clamped to [0, 1].
 */
public record AffinityBreakdown(double dependency, double naming, double pkg, double entity, double endpoint,
                                double typeUsage, double crossDomainPenalty, double total) {

    public static final AffinityBreakdown NONE = new AffinityBreakdown(0, 0, 0, 0, 0, 0, 0, 0);

    /** Short human-readable explanation of the strongest signals. */
    public String explain() {
        StringBuilder text = new StringBuilder();
        append(text, "dependencies", dependency);
        append(text, "naming", naming);
        append(text, "package", pkg);
        append(text, "entity", entity);
        append(text, "endpoint", endpoint);
        append(text, "shared types", typeUsage);
        if (crossDomainPenalty > 0) {
            text.append(text.isEmpty() ? "" : ", ").append("different domain terms");
        }
        return text.isEmpty() ? "no signal" : text.toString();
    }

    private static void append(StringBuilder text, String label, double value) {
        if (value >= 0.4) {
            text.append(text.isEmpty() ? "" : ", ").append(label).append(String.format(" %.2f", value));
        }
    }
}
