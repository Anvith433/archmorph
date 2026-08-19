package com.anvith.archmorph.analysis.architecture.rules;

import com.anvith.archmorph.parser.ComponentType;

/**
 * Represents one architecture rule.
 *
 * Example:
 *
 * CONTROLLER -> SERVICE
 * allowed = true
 */
public class LayerRule {

    /*
     * Source Layer
     */
    private final ComponentType source;

    /*
     * Target Layer
     */
    private final ComponentType target;

    /*
     * Is this dependency allowed?
     */
    private final boolean allowed;

    public LayerRule(
            ComponentType source,
            ComponentType target,
            boolean allowed) {

        this.source = source;
        this.target = target;
        this.allowed = allowed;
    }

    public ComponentType getSource() {
        return source;
    }

    public ComponentType getTarget() {
        return target;
    }

    public boolean isAllowed() {
        return allowed;
    }

}