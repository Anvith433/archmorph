package com.anvith.archmorph.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Remembers successful logins for a short time. HTTP Basic sends the password with every request and a bcrypt
 * check costs a few hundred milliseconds at the recommended work factor, which would make the UI (many parallel
 * requests, job polling) slow and CPU-bound.
 *
 * <p>The cache key is an HMAC of user name and password under a random key generated at startup, so the cache
 * holds nothing that could be used to guess a password offline and nothing survives a restart. Only successes
 * are cached; failures always go to bcrypt (and to the login throttle). Entries expire after {@link #TTL}, and the
 * cache is cleared when it reaches {@link #MAX_ENTRIES}.</p>
 */
final class CachingAuthenticationProvider implements AuthenticationProvider {

    static final Duration TTL = Duration.ofMinutes(5);
    static final int MAX_ENTRIES = 1_000;

    private record Entry(Authentication result, long expiresAt) {
    }

    private final AuthenticationProvider delegate;
    private final SecretKeySpec key;
    private final ConcurrentMap<String, Entry> cache = new ConcurrentHashMap<>();

    CachingAuthenticationProvider(AuthenticationProvider delegate) {
        this.delegate = delegate;
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        this.key = new SecretKeySpec(secret, "HmacSHA256");
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken) || authentication.getCredentials() == null) {
            return delegate.authenticate(authentication);
        }
        String cacheKey = mac(authentication.getName() + '\0' + authentication.getCredentials());
        long now = System.currentTimeMillis();
        Entry cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt() > now) {
            return cached.result();
        }
        Authentication result = delegate.authenticate(authentication);
        if (result != null && result.isAuthenticated()) {
            if (cache.size() >= MAX_ENTRIES) {
                cache.clear();
            }
            cache.put(cacheKey, new Entry(result, now + TTL.toMillis()));
        }
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }

    private String mac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
