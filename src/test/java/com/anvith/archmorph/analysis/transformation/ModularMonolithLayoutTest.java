package com.anvith.archmorph.analysis.transformation;

import com.anvith.archmorph.analysis.transformation.target.TargetStrategy;
import com.anvith.archmorph.analysis.validation.LevelResult;
import com.anvith.archmorph.analysis.validation.ValidationLevel;
import com.anvith.archmorph.analysis.validation.ValidationStatus;
import com.anvith.archmorph.support.ArchMorphTest;
import com.anvith.archmorph.support.PipelineRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The MODULAR_MONOLITH layout: module root = public API, sub-packages = internals (Spring Modulith conventions). */
@ArchMorphTest
class ModularMonolithLayoutTest {

    @Autowired
    PipelineRunner runner;

    @TempDir
    Path temp;

    @Test
    void typesUsedByOtherModulesFormTheModuleApi() throws Exception {
        PipelineRunner.Run run = runner.run("spring-layered", temp, TargetStrategy.MODULAR_MONOLITH);
        Map<String, String> moves = run.plan().getClassMap();

        // used by other modules → module root (public API)
        assertThat(moves).containsEntry("com.demo.entity.User", "com.demo.user.User");
        assertThat(moves).containsEntry("com.demo.service.UserService", "com.demo.user.UserService");
        assertThat(moves).containsEntry("com.demo.service.OrderService", "com.demo.order.OrderService");
        // used only inside the module → internal sub-package
        assertThat(moves).containsEntry("com.demo.controller.UserController", "com.demo.user.controller.UserController");
        assertThat(moves).containsEntry("com.demo.repository.UserRepository", "com.demo.user.repository.UserRepository");
        assertThat(moves).containsEntry("com.demo.entity.Payment", "com.demo.payment.entity.Payment");
        // shared API and shared internals
        assertThat(moves).containsEntry("com.demo.common.ApiResponse", "com.demo.shared.ApiResponse");
        assertThat(moves).containsEntry("com.demo.config.SecurityConfig", "com.demo.shared.security.SecurityConfig");
        // application wiring that depends on modules goes to the root package, outside every module
        assertThat(moves).containsEntry("com.demo.exception.GlobalExceptionHandler", "com.demo.GlobalExceptionHandler");
        assertThat(moves).doesNotContainKey("com.demo.DemoApplication");
    }

    @Test
    void crossModuleDependenciesOnlyTargetModuleApis() throws Exception {
        PipelineRunner.Run run = runner.run("spring-layered", temp, TargetStrategy.MODULAR_MONOLITH);
        LevelResult architecture = run.validation().levels().stream()
                .filter(l -> l.level() == ValidationLevel.ARCHITECTURE_RULES).findFirst().orElseThrow();
        assertThat(architecture.status()).as(architecture.issues().toString()).isEqualTo(ValidationStatus.PASS);
        assertThat(architecture.summary()).contains("go through module APIs");
    }
}
