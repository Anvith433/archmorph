package com.anvith.archmorph.analysis.architecture.rules;

import com.anvith.archmorph.analysis.architecture.Severity;
import com.anvith.archmorph.parser.ComponentType;

/**
 * One architecture rule, e.g. {@code CONTROLLER -> REPOSITORY, allowed = false}.
 */
public class LayerRule {

    private final ComponentType source;
    private final ComponentType target;
    private final boolean allowed;
    private final Severity severity;
    private final String rationale;

    public LayerRule(ComponentType source, ComponentType target, boolean allowed) {
        this(source, target, allowed, Severity.MEDIUM, null);
    }

    public LayerRule(ComponentType source, ComponentType target, boolean allowed, Severity severity, String rationale) {
        this.source = source;
        this.target = target;
        this.allowed = allowed;
        this.severity = severity;
        this.rationale = rationale;
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

    public Severity getSeverity() {
        return severity;
    }

    public String getRationale() {
        return rationale;
    }
}
