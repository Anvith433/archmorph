package com.anvith.archmorph.analysis.architecture.rules;

import com.anvith.archmorph.parser.ComponentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default Layered Architecture Rules.
 */
public final class DefaultLayerRules {

    private DefaultLayerRules() {
    }

    private static final List<LayerRule> RULES =
            new ArrayList<>();

    static {

        /*
         * Controller Layer
         */
        RULES.add(new LayerRule(
                ComponentType.CONTROLLER,
                ComponentType.SERVICE,
                true));

        RULES.add(new LayerRule(
                ComponentType.CONTROLLER,
                ComponentType.DTO,
                true));

        RULES.add(new LayerRule(
                ComponentType.CONTROLLER,
                ComponentType.REPOSITORY,
                false));

        RULES.add(new LayerRule(
                ComponentType.CONTROLLER,
                ComponentType.ENTITY,
                false));

        /*
         * Service Layer
         */
        RULES.add(new LayerRule(
                ComponentType.SERVICE,
                ComponentType.REPOSITORY,
                true));

        RULES.add(new LayerRule(
                ComponentType.SERVICE,
                ComponentType.ENTITY,
                true));

        RULES.add(new LayerRule(
                ComponentType.SERVICE,
                ComponentType.DTO,
                true));

        /*
         * Repository Layer
         */
        RULES.add(new LayerRule(
                ComponentType.REPOSITORY,
                ComponentType.ENTITY,
                true));

        RULES.add(new LayerRule(
                ComponentType.REPOSITORY,
                ComponentType.CONTROLLER,
                false));

        RULES.add(new LayerRule(
                ComponentType.REPOSITORY,
                ComponentType.DTO,
                false));

        /*
         * Entity Layer
         */
        RULES.add(new LayerRule(
                ComponentType.ENTITY,
                ComponentType.CONTROLLER,
                false));

        RULES.add(new LayerRule(
                ComponentType.ENTITY,
                ComponentType.SERVICE,
                false));

        /*
         * DTO Layer
         */
        RULES.add(new LayerRule(
                ComponentType.DTO,
                ComponentType.REPOSITORY,
                false));

        RULES.add(new LayerRule(
                ComponentType.DTO,
                ComponentType.ENTITY,
                true));
    }

    /**
     * Return all rules.
     */
    public static List<LayerRule> getRules() {

        return Collections.unmodifiableList(RULES);

    }

}