package com.anvith.archmorph.analysis.architecture;

import com.anvith.archmorph.parser.ComponentType;

/**
 * Static-analysis coupling indicators of one class.
 *
 * @param afferentCoupling Ca: number of project classes depending on this class
 * @param efferentCoupling Ce: number of project classes this class depends on
 * @param instability      I = Ce / (Ca + Ce), 0 when isolated
 */
public record ClassMetric(String qualifiedName, String className, ComponentType componentType,
                          int afferentCoupling, int efferentCoupling, double instability) {
}
