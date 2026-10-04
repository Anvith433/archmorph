package com.anvith.archmorph.analysis.transformation.target;

import com.anvith.archmorph.analysis.module.ClassAssignment;
import com.anvith.archmorph.analysis.module.ModuleCategory;
import com.anvith.archmorph.analysis.module.ModuleDiscoveryReport;
import com.anvith.archmorph.analysis.transformation.FolderClassifier;
import com.anvith.archmorph.analysis.transformation.FolderType;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.parser.ComponentType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code MODULAR_BY_DOMAIN}:
 *
 * <pre>
 * &lt;base&gt;.modules.&lt;module&gt;.{controller|service|repository|entity|dto|exception|component|common}
 * &lt;base&gt;.shared.{config|security|exception|infrastructure|common}
 * &lt;application class stays in its package: it is the component-scan root&gt;
 * </pre>
 *
 * <p>The {@code @SpringBootApplication} class is deliberately not moved: Spring scans the
 * application class' package and below, so moving it below the root would silently stop
 * scanning every other class.</p>
 */
@Component
public class ModularByDomainArchitecture implements TargetArchitecture {

    private final FolderClassifier folderClassifier;
    private final ArchMorphProperties properties;

    public ModularByDomainArchitecture(FolderClassifier folderClassifier, ArchMorphProperties properties) {
        this.folderClassifier = folderClassifier;
        this.properties = properties;
    }

    @Override
    public TargetStrategy strategy() {
        return TargetStrategy.MODULAR_BY_DOMAIN;
    }

    @Override
    public TargetPlacement place(String basePackage, ClassAssignment assignment, ComponentType type, String currentPackage) {
        ModuleCategory category = assignment.category();
        if (category == ModuleCategory.APPLICATION) {
            return new TargetPlacement(currentPackage, FolderType.COMMON);
        }
        FolderType folder = folderClassifier.classify(type);
        if (category == ModuleCategory.BUSINESS_MODULE) {
            return new TargetPlacement(join(basePackage, properties.getTransformation().getModulesPackage(),
                    assignment.moduleName(), folder.getFolderName()), folder);
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
        return new TargetPlacement(join(basePackage, properties.getTransformation().getSharedPackage(),
                sharedFolder.getFolderName()), sharedFolder);
    }

    @Override
    public List<String> describeLayout(String basePackage, List<String> businessModules) {
        List<String> lines = new ArrayList<>();
        String modules = properties.getTransformation().getModulesPackage();
        lines.add(basePackage.isEmpty() ? "(default package)" : basePackage);
        lines.add("├── " + modules);
        for (String module : businessModules) {
            lines.add("│   ├── " + module + "  (controller, service, repository, entity, dto)");
        }
        lines.add("└── " + properties.getTransformation().getSharedPackage() + "  (config, security, exception, infrastructure, common)");
        return lines;
    }

    private static String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('.');
            }
            builder.append(part);
        }
        return builder.toString();
    }
}
