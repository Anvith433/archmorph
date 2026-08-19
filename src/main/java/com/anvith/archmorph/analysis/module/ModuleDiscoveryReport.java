package com.anvith.archmorph.analysis.module;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleDiscoveryReport {

    /*
     * All discovered modules.
     *
     * Key:
     * Module Name
     *
     * Value:
     * ModuleInfo
     */
    private final Map<String, ModuleInfo> modules =
            new LinkedHashMap<>();

    /**
     * Add module if absent.
     */
    public ModuleInfo getOrCreateModule(String moduleName) {

        return modules.computeIfAbsent(
                moduleName,
                ModuleInfo::new
        );

    }

    /**
 * Find module by name.
 */
public ModuleInfo getModule(String moduleName) {

    return modules.get(moduleName);

}

    /**
     * All modules.
     */
    public Collection<ModuleInfo> getModules() {

        return modules.values();

    }

    /**
     * Number of modules.
     */
    public int getModuleCount() {

        return modules.size();

    }

    /**
     * Is report empty?
     */
    public boolean isEmpty() {

        return modules.isEmpty();

    }

    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder();

        builder.append("\n");
        builder.append("========== DISCOVERED MODULES ==========\n");

        if (modules.isEmpty()) {

            builder.append("No modules discovered.\n");

        } else {

            for (ModuleInfo module : modules.values()) {

                builder.append(module)
                        .append("\n");

            }

        }

        builder.append("========================================");

        return builder.toString();

    }

}