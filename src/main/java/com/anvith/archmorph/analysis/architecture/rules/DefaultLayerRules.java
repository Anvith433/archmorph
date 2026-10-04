package com.anvith.archmorph.analysis.architecture.rules;

import com.anvith.archmorph.analysis.architecture.Severity;
import com.anvith.archmorph.parser.ComponentType;

import java.util.List;

import static com.anvith.archmorph.parser.ComponentType.CONTROLLER;
import static com.anvith.archmorph.parser.ComponentType.DTO;
import static com.anvith.archmorph.parser.ComponentType.ENTITY;
import static com.anvith.archmorph.parser.ComponentType.REPOSITORY;
import static com.anvith.archmorph.parser.ComponentType.SERVICE;

/**
 * Default layered-architecture rules. Pairs without a rule are allowed.
 */
public final class DefaultLayerRules {

    private DefaultLayerRules() {
    }

    private static final List<LayerRule> RULES = List.of(
            /* Controller layer */
            new LayerRule(CONTROLLER, SERVICE, true),
            new LayerRule(CONTROLLER, DTO, true),
            new LayerRule(CONTROLLER, REPOSITORY, false, Severity.MEDIUM,
                    "Controllers should reach persistence through a service."),
            new LayerRule(CONTROLLER, ENTITY, false, Severity.LOW,
                    "Controllers exposing entities couple the API to the persistence model."),

            /* Service layer */
            new LayerRule(SERVICE, REPOSITORY, true),
            new LayerRule(SERVICE, ENTITY, true),
            new LayerRule(SERVICE, DTO, true),
            new LayerRule(SERVICE, CONTROLLER, false, Severity.HIGH,
                    "Services must not depend on the web layer."),

            /* Repository layer */
            new LayerRule(REPOSITORY, ENTITY, true),
            new LayerRule(REPOSITORY, CONTROLLER, false, Severity.HIGH,
                    "Persistence must not depend on the web layer."),
            new LayerRule(REPOSITORY, SERVICE, false, Severity.HIGH,
                    "Persistence must not depend on the business layer."),
            new LayerRule(REPOSITORY, DTO, false, Severity.LOW,
                    "Repositories usually return entities or projections, not API DTOs."),

            /* Entity layer */
            new LayerRule(ENTITY, CONTROLLER, false, Severity.HIGH,
                    "Entities must not depend on the web layer."),
            new LayerRule(ENTITY, SERVICE, false, Severity.HIGH,
                    "Entities must not depend on services."),
            new LayerRule(ENTITY, REPOSITORY, false, Severity.HIGH,
                    "Entities must not depend on repositories."),

            /* DTO layer */
            new LayerRule(DTO, REPOSITORY, false, Severity.MEDIUM,
                    "DTOs are plain data carriers."),
            new LayerRule(DTO, SERVICE, false, Severity.MEDIUM,
                    "DTOs are plain data carriers."),
            new LayerRule(DTO, ENTITY, true));

    public static List<LayerRule> getRules() {
        return RULES;
    }

    /** Unused helper retained for API stability. */
    public static ComponentType[] layers() {
        return new ComponentType[]{CONTROLLER, SERVICE, REPOSITORY, ENTITY, DTO};
    }
}
