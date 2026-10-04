package com.anvith.archmorph.api.dto;

import java.util.List;
import java.util.Map;

/** Transformation plan and dry-run views. Paths are always project-relative. */
public final class PlanDtos {

    private PlanDtos() {
    }

    public record ClassMoveDto(String source, String target, boolean nested) {
    }

    public record PlanEntryDto(String id, String scope, String className, String sourcePath, String targetPath,
                               String sourcePackage, String targetPackage, String module, String folder,
                               List<String> actions, String safety, String risk, double confidence,
                               List<String> rewrites, List<String> reasons, List<ClassMoveDto> classes) {
    }

    public record ConflictDto(String type, String target, List<String> sources, String resolution) {
    }

    public record ResourceFindingDto(String file, int line, String reference, String snippet) {
    }

    public record PlanSummaryDto(int files, int moved, int kept, int excluded, int manualReview, int unsupported,
                                 int safe, int safeWithWarning, int conflicts, int rewrites) {
    }

    public record PlanDto(String strategy, String basePackage, String fingerprint, PlanSummaryDto summary,
                          List<PlanEntryDto> entries, List<ConflictDto> conflicts, List<String> warnings,
                          List<ResourceFindingDto> resourceFindings, List<String> layout,
                          Map<String, String> classMap, boolean modulithVerification, List<String> generatedFiles) {
    }

    public record FileChangeDto(String entryId, String sourcePath, String targetPath, boolean changed,
                                boolean packageChanged, List<String> importChanges, int qualifiedRewrites,
                                int linesAdded, int linesRemoved, List<String> warnings) {
    }

    /** Body of {@code PUT /projects/{id}/strategy}. */
    /**
     * Body of {@code PUT /projects/{id}/strategy}. {@code addModulithVerification} (MODULAR_MONOLITH only) adds
     * Spring Modulith's test dependency and a ModularityTests class; null keeps the current choice.
     */
    public record ChangeStrategyRequest(@jakarta.validation.constraints.NotNull
                                        com.anvith.archmorph.analysis.transformation.target.TargetStrategy strategy,
                                        Boolean addModulithVerification) {
    }

    public record DryRunDto(PlanDto plan, List<FileChangeDto> files, List<String> warnings, long durationMillis) {
    }

    public record DiffDto(String entryId, String className, String sourcePath, String targetPath, String before,
                          String after, String unified, boolean changed, boolean truncated,
                          List<String> importChanges, int qualifiedRewrites) {
    }
}
