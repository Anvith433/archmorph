package com.anvith.archmorph.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** Module discovery views and the module editing request. */
public final class ModuleDtos {

    private ModuleDtos() {
    }

    public record ModuleClassDto(String qualifiedName, String className, String componentType, double confidence,
                                 String origin, boolean locked, boolean excluded, List<String> reasons) {
    }

    public record ModuleDto(String name, String category, double confidence, double cohesion, double externalCoupling,
                            int classCount, int internalDependencies, int externalDependencies,
                            Map<String, Integer> dependenciesOnModules, List<String> evidence, List<String> warnings,
                            List<ModuleClassDto> classes) {
    }

    /** One user decision; see {@code ModuleEdit}. Unused fields stay null. */
    public record ModuleEditDto(@NotNull String type, @Size(max = 80) String module, @Size(max = 80) String newName,
                                @Size(max = 80) String target, @Size(max = 50) List<@Size(max = 80) String> sources,
                                @Size(max = 300) String className, @Size(max = 500) List<@Size(max = 300) String> classes) {
    }

    public record UpdateModulesRequest(@NotNull @Size(max = 500) List<ModuleEditDto> edits) {
    }

    public record ModulesDto(List<ModuleDto> suggestion, List<ModuleEditDto> decisions, List<ModuleDto> finalModules,
                             List<String> warnings, String note) {
    }
}
