package com.anvith.archmorph.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CachingAuthenticationProviderTest {

    private final AtomicInteger checks = new AtomicInteger();

    /** Stands in for the bcrypt check: accepts ada / right. */
    private final AuthenticationProvider bcrypt = new AuthenticationProvider() {
        @Override
        public Authentication authenticate(Authentication authentication) {
            checks.incrementAndGet();
            if (authentication.getName().equals("ada") && "right".equals(authentication.getCredentials())) {
                return UsernamePasswordAuthenticationToken.authenticated("ada", null, List.of());
            }
            throw new BadCredentialsException("bad");
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
        }
    };

    private final CachingAuthenticationProvider provider = new CachingAuthenticationProvider(bcrypt);

    private static Authentication login(String name, String password) {
        return UsernamePasswordAuthenticationToken.unauthenticated(name, password);
    }

    @Test
    void successfulLoginsAreCheckedOnce() {
        for (int i = 0; i < 5; i++) {
            assertThat(provider.authenticate(login("ada", "right")).isAuthenticated()).isTrue();
        }
        assertThat(checks).hasValue(1);
    }

    @Test
    void failuresAreNeverCachedAndAnotherPasswordIsNotServedFromTheCache() {
        provider.authenticate(login("ada", "right"));

        assertThatThrownBy(() -> provider.authenticate(login("ada", "wrong"))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> provider.authenticate(login("ada", "wrong"))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> provider.authenticate(login("ad", "aright"))).isInstanceOf(BadCredentialsException.class);
        assertThat(checks).as("every failed attempt reaches bcrypt").hasValue(4);
    }
}
