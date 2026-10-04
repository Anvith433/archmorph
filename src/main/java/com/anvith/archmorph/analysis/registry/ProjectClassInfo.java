package com.anvith.archmorph.analysis.registry;

import com.anvith.archmorph.parser.ClassMetadata;
import com.anvith.archmorph.parser.ComponentType;
import com.anvith.archmorph.parser.SourceScope;

/**
 * Stores metadata of one class discovered during project scanning.
 * Identity is the canonical (fully-qualified) name.
 */
public class ProjectClassInfo {

    private final String className;

    private final String packageName;

    private final ComponentType componentType;

    private final String qualifiedName;

    /** Top-level type owning this declaration (itself for top-level types). */
    private final String topLevelQualifiedName;

    private final SourceScope scope;

    private final ClassMetadata metadata;

    public ProjectClassInfo(String className, String packageName, ComponentType componentType) {
        this(className, packageName, componentType,
                packageName == null || packageName.isBlank() ? className : packageName + "." + className,
                null, SourceScope.MAIN, null);
    }

    public ProjectClassInfo(String className, String packageName, ComponentType componentType,
                            String qualifiedName, String topLevelQualifiedName, SourceScope scope,
                            ClassMetadata metadata) {
        this.className = className;
        this.packageName = packageName;
        this.componentType = componentType;
        this.qualifiedName = qualifiedName;
        this.topLevelQualifiedName = topLevelQualifiedName == null ? qualifiedName : topLevelQualifiedName;
        this.scope = scope;
        this.metadata = metadata;
    }

    public static ProjectClassInfo of(ClassMetadata metadata) {
        return new ProjectClassInfo(metadata.getClassName(), metadata.getPackageName(), metadata.getComponentType(),
                metadata.getQualifiedName(), metadata.getTopLevelQualifiedName(), metadata.getScope(), metadata);
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    public ComponentType getComponentType() {
        return componentType;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public String getTopLevelQualifiedName() {
        return topLevelQualifiedName;
    }

    public boolean isNested() {
        return !qualifiedName.equals(topLevelQualifiedName);
    }

    public SourceScope getScope() {
        return scope;
    }

    public ClassMetadata getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return qualifiedName;
    }
}
