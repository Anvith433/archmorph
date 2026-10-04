package com.anvith.archmorph.analysis.architecture.rules;

import com.anvith.archmorph.parser.ComponentType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LayerRuleRegistry {

    private final List<LayerRule> rules = DefaultLayerRules.getRules();

    /** Returns true if the dependency is allowed (no rule means allowed). */
    public boolean isAllowed(ComponentType source, ComponentType target) {
        return findRule(source, target).map(LayerRule::isAllowed).orElse(true);
    }

    public Optional<LayerRule> findRule(ComponentType source, ComponentType target) {
        return rules.stream().filter(r -> r.getSource() == source && r.getTarget() == target).findFirst();
    }

    public List<LayerRule> getRules() {
        return rules;
    }
}
