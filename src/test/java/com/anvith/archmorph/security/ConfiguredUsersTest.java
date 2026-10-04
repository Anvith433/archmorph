package com.anvith.archmorph.security;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredUsersTest {

    private static ArchMorphProperties.User user(String name, String hash) {
        ArchMorphProperties.User user = new ArchMorphProperties.User();
        user.setUsername(name);
        user.setPasswordHash(hash);
        return user;
    }

    @Test
    void acceptsBcryptHashesWithOrWithoutPrefix() {
        String hash = new BCryptPasswordEncoder(4).encode("correct horse battery");

        var users = ConfiguredUsers.of(List.of(user("ada", "{bcrypt}" + hash), user("bob", hash)));

        assertThat(users).extracting(u -> u.getUsername()).containsExactly("ada", "bob");
        assertThat(new BCryptPasswordEncoder().matches("correct horse battery", users.getFirst().getPassword())).isTrue();
    }

    @Test
    void refusesPlainTextAndNoopPasswordsWithoutEchoingThem() {
        assertThatThrownBy(() -> ConfiguredUsers.of(List.of(user("ada", "hunter2-plain"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("users[0].password-hash must be a bcrypt hash")
                .hasMessageNotContaining("hunter2");
        assertThatThrownBy(() -> ConfiguredUsers.of(List.of(user("ada", "{noop}secret"))))
                .hasMessageNotContaining("secret");
    }

    @Test
    void refusesMissingDuplicateOrOddUserNames() {
        String hash = new BCryptPasswordEncoder(4).encode("x");
        assertThatThrownBy(() -> ConfiguredUsers.of(List.of())).hasMessageContaining("at least one user");
        assertThatThrownBy(() -> ConfiguredUsers.of(List.of(user("ada", hash), user("ada", hash))))
                .hasMessageContaining("users[1].username");
        assertThatThrownBy(() -> ConfiguredUsers.of(List.of(user("a b", hash)))).hasMessageContaining("users[0].username");
    }

    @Test
    void loopbackDetection() {
        assertThat(ExposureWarning.isLoopback("127.0.0.1")).isTrue();
        assertThat(ExposureWarning.isLoopback("::1")).isTrue();
        assertThat(ExposureWarning.isLoopback(null)).as("unset means all interfaces").isFalse();
        assertThat(ExposureWarning.isLoopback("0.0.0.0")).isFalse();
    }
}
