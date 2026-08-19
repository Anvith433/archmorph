package com.anvith.archmorph.analysis.registry;

import com.anvith.archmorph.parser.ComponentType;

/**
 * Stores metadata of one class
 * discovered during project scanning.
 */
public class ProjectClassInfo {

    /*
     * Class Name
     */
    private final String className;

    /*
     * Package Name
     */
    private final String packageName;

    /*
     * Component Type
     */
    private final ComponentType componentType;

    public ProjectClassInfo(
            String className,
            String packageName,
            ComponentType componentType) {

        this.className = className;
        this.packageName = packageName;
        this.componentType = componentType;
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

}