package com.anvith.archmorph.api.dto;

import java.util.List;
import java.util.Map;

/** Current versus proposed architecture. */
public final class ArchitectureDtos {

    private ArchitectureDtos() {
    }

    public record LayerDto(String name, String componentType, int count, List<String> classes) {
    }

    public record CurrentDto(String packageStyle, List<LayerDto> layers, Map<String, Integer> packages) {
    }

    public record ProposedModuleDto(String name, double confidence, int classCount, Map<String, List<String>> folders) {
    }

    public record ProposedDto(String strategy, String basePackage, List<String> layout,
                              List<ProposedModuleDto> modules, Map<String, List<String>> shared,
                              List<String> application) {
    }

    public record ArchitectureDto(CurrentDto current, ProposedDto proposed, List<String> notes) {
    }
}
