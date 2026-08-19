package com.anvith.archmorph.analysis.registry;

import com.anvith.archmorph.parser.ClassMetadata;
import org.springframework.stereotype.Service;

@Service
public class ProjectClassCollector {

    private final ProjectClassRegistry registry;

    public ProjectClassCollector(
            ProjectClassRegistry registry) {

        this.registry = registry;
    }

    /**
     * Register one discovered class.
     */
    public void collect(
            ClassMetadata metadata) {

        registry.register(

                new ProjectClassInfo(

                        metadata.getClassName(),

                        metadata.getPackageName(),

                        metadata.getComponentType()

                )

        );

    }

}