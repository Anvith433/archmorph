package com.anvith.archmorph.security;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * Warns at startup when the server accepts connections from other machines without authentication: anyone who
 * can reach the port can then upload code that the build level compiles.
 */
@Component
@ConditionalOnWebApplication
class ExposureWarning {

    private static final Logger log = LoggerFactory.getLogger(ExposureWarning.class);

    private final ArchMorphProperties properties;
    private final Environment environment;

    ExposureWarning(ArchMorphProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnIfExposed() {
        if (properties.getSecurity().getAuth().getMode() != ArchMorphProperties.AuthMode.NONE) {
            return;
        }
        String address = environment.getProperty("server.address");
        if (!isLoopback(address)) {
            log.warn("Authentication is disabled and the server listens on {}. Anyone who can reach it can upload "
                    + "projects{}. Restrict who can reach the port (server.address=127.0.0.1, or publish a container port "
                    + "on 127.0.0.1 only) or set archmorph.security.auth.mode=BASIC; see docs/DEPLOYMENT.md.", address == null || address.isBlank() ? "all interfaces" : "a non-loopback address",
                    properties.getValidation().getBuild().isEnabled() ? " whose builds this server runs" : "");
        }
    }

    static boolean isLoopback(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(address.trim()).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
