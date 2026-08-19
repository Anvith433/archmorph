package com.anvith.archmorph.analysis.architecture.rules;

import com.anvith.archmorph.parser.ComponentType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LayerRuleRegistry {

    private final List<LayerRule> rules =
            DefaultLayerRules.getRules();

    /**
     * Returns true if the dependency is allowed.
     */
    public boolean isAllowed(
            ComponentType source,
            ComponentType target) {

        for (LayerRule rule : rules) {

            if (rule.getSource() == source &&
                    rule.getTarget() == target) {

                return rule.isAllowed();
            }
        }

        /*
         * If no rule exists,
         * allow by default.
         */
        return true;
    }

}