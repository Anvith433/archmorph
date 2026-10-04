package com.anvith.archmorph.analysis.transformation.target;

import com.anvith.archmorph.common.exception.ArchMorphException;
import com.anvith.archmorph.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

/** Selects the {@link TargetArchitecture} implementation for a strategy. */
@Service
public class TargetArchitectureResolver {

    private final List<TargetArchitecture> architectures;

    public TargetArchitectureResolver(List<TargetArchitecture> architectures) {
        this.architectures = List.copyOf(architectures);
    }

    public TargetArchitecture resolve(TargetStrategy strategy) {
        return architectures.stream()
                .filter(a -> a.strategy() == strategy)
                .findFirst()
                .orElseThrow(() -> new ArchMorphException(ErrorCode.INVALID_REQUEST,
                        "The target architecture strategy " + strategy + " is not supported yet.",
                        "Use MODULAR_BY_DOMAIN."));
    }

    public TargetArchitecture defaultArchitecture() {
        return resolve(TargetStrategy.MODULAR_BY_DOMAIN);
    }
}
