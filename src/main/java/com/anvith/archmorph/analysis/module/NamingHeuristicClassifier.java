package com.anvith.archmorph.analysis.module;

import org.springframework.stereotype.Component;

@Component
public class NamingHeuristicClassifier
        implements ModuleClassifier {

    @Override
    public String classify(String className) {

        if (className == null || className.isBlank()) {
            return "common";
        }

        String module = className;

        module = module.replace("Controller", "");
        module = module.replace("ServiceImpl", "");
        module = module.replace("Service", "");
        module = module.replace("Repository", "");
        module = module.replace("Entity", "");
        module = module.replace("Dto", "");
        module = module.replace("DTO", "");
        module = module.replace("Request", "");
        module = module.replace("Response", "");
        module = module.replace("Config", "");
        module = module.replace("Configuration", "");
        module = module.replace("Exception", "");

        if (module.isBlank()) {
            module = "common";
        }

        return module.toLowerCase();
    }

}