package com.anvith.archmorph.analysis.module;

import com.anvith.archmorph.analysis.module.editing.ModuleEdit;
import com.anvith.archmorph.analysis.module.editing.ModuleEditService;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlan;
import com.anvith.archmorph.analysis.transformation.planner.TransformationPlanner;
import com.anvith.archmorph.analysis.transformation.target.TargetStrategy;
import com.anvith.archmorph.common.exception.ArchMorphException;
import com.anvith.archmorph.pipeline.AnalysisResult;
import com.anvith.archmorph.support.ArchMorphTest;
import com.anvith.archmorph.support.PipelineRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Users decide which classes form a module's public API (MODULAR_MONOLITH). */
@ArchMorphTest
class ExposureDecisionsTest {

    @Autowired
    PipelineRunner runner;

    @Autowired
    ModuleEditService editService;

    @Autowired
    TransformationPlanner planner;

    @TempDir
    Path temp;

    private static ModuleEdit edit(ModuleEdit.Type type, String className) {
        return new ModuleEdit(type, null, null, null, null, className, null);
    }

    @Test
    void anInternalClassCanBeMadePublicApi() throws Exception {
        AnalysisResult analysis = runner.analyze("spring-layered", temp);
        ModuleDiscoveryReport modules = editService.apply(analysis.suggestion(),
                List.of(edit(ModuleEdit.Type.EXPOSE_CLASS, "com.demo.repository.UserRepository")), analysis.graph());

        TransformationPlan plan = planner.plan(analysis.model(), analysis.graph(), modules, TargetStrategy.MODULAR_MONOLITH);
        assertThat(plan.getClassMap()).containsEntry("com.demo.repository.UserRepository", "com.demo.user.UserRepository");
    }

    @Test
    void anUnusedApiClassCanBeMadeInternal() throws Exception {
        AnalysisResult analysis = runner.analyze("spring-layered", temp);
        // Payment is not used by other modules, so marking it internal is allowed and changes nothing harmful
        ModuleDiscoveryReport modules = editService.apply(analysis.suggestion(),
                List.of(edit(ModuleEdit.Type.EXPOSE_CLASS, "com.demo.entity.Payment"),
                        edit(ModuleEdit.Type.INTERNAL_CLASS, "com.demo.entity.Payment")), analysis.graph());
        TransformationPlan plan = planner.plan(analysis.model(), analysis.graph(), modules, TargetStrategy.MODULAR_MONOLITH);
        assertThat(plan.getClassMap()).containsEntry("com.demo.entity.Payment", "com.demo.payment.entity.Payment");
        assertThat(modules.getExposureOverrides()).containsEntry("com.demo.entity.Payment", Exposure.INTERNAL);

        ModuleDiscoveryReport reset = editService.apply(analysis.suggestion(),
                List.of(edit(ModuleEdit.Type.INTERNAL_CLASS, "com.demo.entity.Payment"),
                        edit(ModuleEdit.Type.AUTO_EXPOSURE, "com.demo.entity.Payment")), analysis.graph());
        assertThat(reset.getExposureOverrides()).isEmpty();
    }

    @Test
    void aClassUsedByAnotherModuleCannotBeInternal() throws Exception {
        AnalysisResult analysis = runner.analyze("spring-layered", temp);
        assertThatThrownBy(() -> editService.apply(analysis.suggestion(),
                List.of(edit(ModuleEdit.Type.INTERNAL_CLASS, "com.demo.service.UserService")), analysis.graph()))
                .isInstanceOf(ArchMorphException.class)
                .hasMessageContaining("UserService cannot be internal")
                .hasMessageContaining("(order)");
    }
}
