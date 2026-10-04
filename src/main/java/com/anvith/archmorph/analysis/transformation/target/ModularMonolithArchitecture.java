package com.anvith.archmorph.analysis.transformation.target;

import com.anvith.archmorph.analysis.module.ClassAssignment;
import com.anvith.archmorph.analysis.module.ModuleCategory;
import com.anvith.archmorph.analysis.transformation.FolderClassifier;
import com.anvith.archmorph.analysis.transformation.FolderType;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.parser.ComponentType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.anvith.archmorph.analysis.transformation.target.ModularByDomainArchitecture.join;

/**
 * {@code MODULAR_MONOLITH}: the layout of a Spring Modulith application.
 *
 * <pre>
 * &lt;base&gt;                         application class (component-scan root, never moved)
 * &lt;base&gt;.&lt;module&gt;                public API of the module: the types other modules use
 * &lt;base&gt;.&lt;module&gt;.&lt;folder&gt;       internals: controller, service, repository, entity, dto, ...
 * &lt;base&gt;.shared                  shared types used by modules
 * &lt;base&gt;.shared.&lt;folder&gt;         shared internals: config, security, exception, infrastructure, common
 * </pre>
 *
 * <p>Spring Modulith treats every direct sub-package of the application package as a module and only the
 * module's base package as accessible to other modules, so {@code ApplicationModules.of(App.class).verify()}
 * can check the result. Placement of a class depends on whether code outside its module uses it: used from
 * outside → module API, otherwise → internal folder. ArchMorph never changes visibility; it only decides
 * packages, so the module APIs describe the dependencies that exist today.</p>
 */
@Component
public class ModularMonolithArchitecture implements TargetArchitecture {

    private final FolderClassifier folderClassifier;
    private final ArchMorphProperties properties;

    public ModularMonolithArchitecture(FolderClassifier folderClassifier, ArchMorphProperties properties) {
        this.folderClassifier = folderClassifier;
        this.properties = properties;
    }

    @Override
    public TargetStrategy strategy() {
        return TargetStrategy.MODULAR_MONOLITH;
    }

    @Override
    public boolean separatesApi() {
        return true;
    }

    @Override
    public TargetPlacement place(String basePackage, ClassAssignment assignment, ComponentType type, String currentPackage) {
        return place(basePackage, assignment, type, currentPackage, false);
    }

    @Override
    public TargetPlacement place(String basePackage, ClassAssignment assignment, ComponentType type, String currentPackage,
                                 boolean exposed) {
        ModuleCategory category = assignment.category();
        if (category == ModuleCategory.APPLICATION) {
            // the entry point stays where it is (it defines the component-scan root); other application
            // wiring goes directly into the root package, which belongs to no module
            return new TargetPlacement(type == ComponentType.APPLICATION ? currentPackage : basePackage, FolderType.COMMON);
        }
        FolderType folder = folderClassifier.classify(type);
        if (category == ModuleCategory.BUSINESS_MODULE) {
            String root = moduleRoot(basePackage, assignment.moduleName());
            return exposed ? new TargetPlacement(root, folder) : new TargetPlacement(join(root, folder.getFolderName()), folder);
        }
        FolderType sharedFolder = switch (category) {
            case CONFIGURATION -> FolderType.CONFIGURATION;
            case SECURITY -> FolderType.SECURITY;
            case INFRASTRUCTURE -> FolderType.INFRASTRUCTURE;
            default -> switch (folder) {
                case EXCEPTION -> FolderType.EXCEPTION;
                case CONFIGURATION -> FolderType.CONFIGURATION;
                case SECURITY -> FolderType.SECURITY;
                default -> FolderType.COMMON;
            };
        };
        String root = sharedRoot(basePackage);
        return exposed ? new TargetPlacement(root, sharedFolder) : new TargetPlacement(join(root, sharedFolder.getFolderName()), sharedFolder);
    }

    @Override
    public String moduleRoot(String basePackage, String module) {
        return join(basePackage, module);
    }

    @Override
    public String sharedRoot(String basePackage) {
        return join(basePackage, properties.getTransformation().getSharedPackage());
    }

    @Override
    public List<String> describeLayout(String basePackage, List<String> businessModules) {
        List<String> lines = new ArrayList<>();
        lines.add((basePackage.isEmpty() ? "(default package)" : basePackage) + "  (application class)");
        for (String module : businessModules) {
            lines.add("├── " + module + "  (public API: types other modules use)");
            lines.add("│   └── controller, service, repository, entity, dto  (internal)");
        }
        lines.add("└── " + properties.getTransformation().getSharedPackage() + "  (shared API)");
        lines.add("    └── config, security, exception, infrastructure, common  (internal)");
        return lines;
    }
}
