package com.anvith.archmorph.analysis.registry;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class DefaultProjectClassRegistry
        implements ProjectClassRegistry {

    /*
     * className -> metadata
     */
    private final Map<String, ProjectClassInfo> registry =
            new HashMap<>();

    @Override
    public void register(ProjectClassInfo classInfo) {

        if (classInfo == null) {
            return;
        }

        registry.put(
                classInfo.getClassName(),
                classInfo
        );
    }

    @Override
    public ProjectClassInfo find(String className) {

        return registry.get(className);

    }

    @Override
    public boolean contains(String className) {

        return registry.containsKey(className);

    }

    @Override
    public void clear() {

        registry.clear();

    }

}