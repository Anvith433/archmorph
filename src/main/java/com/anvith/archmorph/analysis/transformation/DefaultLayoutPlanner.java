package com.anvith.archmorph.analysis.transformation;

import com.anvith.archmorph.analysis.dependency.DependencyNode;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.module.ModuleInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultLayoutPlanner
        implements LayoutPlanner {

    private final FolderClassifier folderClassifier;

    @Override
    public ModuleLayoutReport plan(
            ModuleDiscoveryReport moduleReport) {

        ModuleLayoutReport report =
                new ModuleLayoutReport();

        for (ModuleInfo module : moduleReport.getModules()) {

            ModuleLayout layout =
                    report.getOrCreateLayout(
                            module.getModuleName()
                    );

            for (DependencyNode node : module.getClasses()) {

                FolderType folder =
                        folderClassifier.classify(
                                node.getComponentType()
                        );

                layout.addClass(
                        folder,
                        node.getClassName()
                );
            }
        }

        return report;
    }

}