package com.anvith.archmorph.parser;

import java.util.List;

/**
 * Result of classifying a type into an architectural role.
 *
 * @param type       the most likely role
 * @param confidence 0..1, how strongly the evidence supports {@code type}
 * @param evidence   human-readable reasons, strongest first
 */
public record ComponentClassification(ComponentType type, double confidence, List<String> evidence) {

    public static ComponentClassification unknown(String reason) {
        return new ComponentClassification(ComponentType.UNKNOWN, 0.0, List.of(reason));
    }
}
