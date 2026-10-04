package com.anvith.archmorph.analysis.module;

import com.anvith.archmorph.analysis.module.boundary.BoundaryAdvisor;
import com.anvith.archmorph.analysis.module.boundary.BoundaryReport;
import com.anvith.archmorph.analysis.module.boundary.BoundarySuggestion;
import com.anvith.archmorph.analysis.module.boundary.SuggestionKind;
import com.anvith.archmorph.analysis.module.editing.ModuleEdit;
import com.anvith.archmorph.analysis.module.editing.ModuleEditService;
import com.anvith.archmorph.pipeline.AnalysisResult;
import com.anvith.archmorph.support.ArchMorphTest;
import com.anvith.archmorph.support.PipelineRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ArchMorphTest
class BoundaryAdvisorTest {

    @Autowired
    PipelineRunner runner;

    @Autowired
    BoundaryAdvisor advisor;

    @Autowired
    ModuleEditService editService;

    @TempDir
    Path temp;

    @Test
    void acyclicModulesNeedNoSuggestions() throws Exception {
        AnalysisResult analysis = runner.analyze("spring-layered", temp);
        BoundaryReport report = advisor.advise(analysis.suggestion(), analysis.graph(), analysis.facts());
        assertThat(report.acyclic()).isTrue();
        assertThat(report.suggestions()).isEmpty();
    }

    @Test
    void facadesAreSplitAndBidirectionalEntitiesMadeOneDirectional() throws Exception {
        AnalysisResult analysis = runner.analyze("realworld-layered", temp);
        BoundaryReport report = advisor.advise(analysis.suggestion(), analysis.graph(), analysis.facts());

        assertThat(report.cycles()).isNotEmpty();
        BoundarySuggestion facade = report.suggestions().stream()
                .filter(s -> s.kind() == SuggestionKind.SPLIT_FACADE).findFirst().orElseThrow();
        assertThat(facade.subject()).isEqualTo("com.clinic.service.ClinicService");
        assertThat(String.join("\n", facade.steps()))
                .contains("findOwnerById", "'owner'")
                .contains("findPetById", "savePet", "'pet'")
                .contains("findVets", "'vet'");
        assertThat(facade.edit()).isNull();

        // the facade family is handled once, not once per module it touches
        assertThat(report.suggestions()).filteredOn(s -> s.kind() == SuggestionKind.SPLIT_FACADE).hasSize(1);
        assertThat(report.suggestions()).allSatisfy(s -> assertThat(s.id()).matches("b-\\d{3}"));
    }

    @Test
    void aMisplacedClassIsMovedBack() throws Exception {
        AnalysisResult analysis = runner.analyze("spring-layered", temp);
        // put OrderService into 'user': order's controller now needs user, and user needs order's entity and repository
        ModuleDiscoveryReport edited = editService.apply(analysis.suggestion(), List.of(
                new ModuleEdit(ModuleEdit.Type.MOVE_CLASS, null, null, "user", null, "com.demo.service.OrderService", null)),
                analysis.graph());

        BoundaryReport report = advisor.advise(edited, analysis.graph(), analysis.facts());

        assertThat(report.cycles()).anySatisfy(c -> assertThat(c).contains("order", "user"));
        BoundarySuggestion move = report.suggestions().stream()
                .filter(s -> s.kind() == SuggestionKind.MOVE_CLASS).findFirst().orElseThrow();
        assertThat(move.subject()).isEqualTo("com.demo.service.OrderService");
        assertThat(move.edit()).isNotNull();
        assertThat(move.edit().type()).isEqualTo(ModuleEdit.Type.MOVE_CLASS);
        assertThat(move.edit().target()).isEqualTo("order");

        // applying the suggested edit removes the cycle
        ModuleDiscoveryReport fixed = editService.apply(analysis.suggestion(), List.of(
                new ModuleEdit(ModuleEdit.Type.MOVE_CLASS, null, null, "user", null, "com.demo.service.OrderService", null),
                move.edit()), analysis.graph());
        assertThat(advisor.advise(fixed, analysis.graph(), analysis.facts()).acyclic()).isTrue();
    }
}
