package com.anvith.archmorph.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** Module discovery views and the module editing request. */
public final class ModuleDtos {

    private ModuleDtos() {
    }

    /**
     * @param exposure         PUBLIC_API when the class belongs to its module's public API (MODULAR_MONOLITH layout:
     *                         the module root package), otherwise INTERNAL
     * @param exposureOverride the user's decision (PUBLIC_API / INTERNAL), or null when automatic
     */
    public record ModuleClassDto(String qualifiedName, String className, String componentType, double confidence,
                                 String origin, boolean locked, boolean excluded, List<String> reasons,
                                 String exposure, String exposureOverride) {
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

    /**
     * A project's review decisions as a portable file: target layout, Spring Modulith option and module edits.
     * Exported by {@code GET /decisions}, applied by {@code PUT /decisions} and by the CLI's {@code --decisions}.
     */
    public record DecisionsDto(int version, String strategy, Boolean addModulithVerification,
                               @NotNull @Size(max = 500) List<ModuleEditDto> edits) {

        public static final int CURRENT_VERSION = 1;
    }

    public static com.anvith.archmorph.analysis.module.editing.ModuleEdit toEdit(ModuleEditDto dto) {
        com.anvith.archmorph.analysis.module.editing.ModuleEdit.Type type;
        try {
            type = com.anvith.archmorph.analysis.module.editing.ModuleEdit.Type.valueOf(dto.type());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new com.anvith.archmorph.common.exception.ArchMorphException(
                    com.anvith.archmorph.common.exception.ErrorCode.INVALID_MODULE_OPERATION, "Unknown module operation.");
        }
        return new com.anvith.archmorph.analysis.module.editing.ModuleEdit(type, dto.module(), dto.newName(), dto.target(),
                dto.sources(), dto.className(), dto.classes());
    }

    public static ModuleEditDto fromEdit(com.anvith.archmorph.analysis.module.editing.ModuleEdit edit) {
        return new ModuleEditDto(edit.type().name(), edit.module(), edit.newName(), edit.target(), edit.sources(),
                edit.className(), edit.classes());
    }

    public record UpdateModulesRequest(@NotNull @Size(max = 500) List<ModuleEditDto> edits) {
    }

    /** A proposal for breaking one module dependency that closes a cycle; {@code edit} applies it when present. */
    public record BoundarySuggestionDto(String id, String kind, String from, String to, String subject, String title,
                                        String rationale,
                                        int dependencyCount, List<String> steps, List<String> evidence, ModuleEditDto edit) {
    }

    public record ModulesDto(List<ModuleDto> suggestion, List<ModuleEditDto> decisions, List<ModuleDto> finalModules,
                             List<String> warnings, String note, List<List<String>> cycles,
                             List<BoundarySuggestionDto> boundarySuggestions, String strategy) {
    }
}
