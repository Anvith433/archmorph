package com.anvith.archmorph.analysis.module.naming;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Default assistant: no AI provider configured. */
@Component
public class NoOpModuleNamingAssistant implements ModuleNamingAssistant {

    @Override
    public Optional<ModuleNameSuggestion> suggestName(String currentName, List<String> classNames) {
        return Optional.empty();
    }
}
