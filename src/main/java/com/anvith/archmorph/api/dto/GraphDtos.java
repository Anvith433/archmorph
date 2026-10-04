package com.anvith.archmorph.api.dto;

import java.util.List;

/** Dependency graph for the interactive view. */
public final class GraphDtos {

    private GraphDtos() {
    }

    public record NodeDto(String id, String className, String packageName, String componentType, String module,
                          String category, double confidence, int afferentCoupling, int efferentCoupling,
                          boolean inCycle, String file) {
    }

    public record EdgeDto(String id, String source, String target, String type, int occurrences, double confidence,
                          boolean crossModule, boolean violation, boolean inCycle, String location) {
    }

    public record GraphDto(List<NodeDto> nodes, List<EdgeDto> edges, int totalNodes, int totalEdges, boolean truncated) {
    }
}
