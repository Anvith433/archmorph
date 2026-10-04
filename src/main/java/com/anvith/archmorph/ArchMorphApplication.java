package com.anvith.archmorph;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ArchMorphProperties.class)
public class ArchMorphApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchMorphApplication.class, args);
    }

}
