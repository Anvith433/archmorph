package com.anvith.archmorph.api.web;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Identifies the caller for rate limiting and job ownership. With authentication enabled this is a hash of
 * the signed-in user's name; in the local no-login mode (and before authentication) it is a hash of the
 * network address, so raw addresses and names never appear in logs or memory structures.
 * {@code X-Forwarded-For} is only honoured when {@code archmorph.security.trust-forwarded-headers}
 * is enabled (i.e. behind a trusted reverse proxy).
 */
@Component
public class ClientResolver {

    private final ArchMorphProperties properties;

    public ClientResolver(ArchMorphProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "u" + hash("user:" + authentication.getName());
        }
        return networkClient(request);
    }

    /** Hash of the caller's network address, whether or not it is signed in. */
    public String networkClient(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        if (properties.getSecurity().isTrustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                address = forwarded.split(",")[0].trim();
            }
        }
        return hash(String.valueOf(address));
    }

    private static String hash(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
