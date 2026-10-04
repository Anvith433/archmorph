package com.anvith.archmorph.security;

import com.anvith.archmorph.common.config.ArchMorphProperties;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns the configured users into {@link UserDetails}, refusing to start on anything unsafe: no users, blank or
 * duplicate names, or a password that is not a bcrypt hash (plain text and {@code {noop}} are rejected). Error
 * messages name the user index, never the value.
 */
final class ConfiguredUsers {

    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._@-]{1,64}$");

    private ConfiguredUsers() {
    }

    static List<UserDetails> of(List<ArchMorphProperties.User> configured) {
        if (configured == null || configured.isEmpty()) {
            throw new IllegalStateException("archmorph.security.auth.mode=BASIC requires at least one user "
                    + "(archmorph.security.auth.users[0].username / password-hash).");
        }
        List<UserDetails> users = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < configured.size(); i++) {
            ArchMorphProperties.User user = configured.get(i);
            String name = user.getUsername() == null ? "" : user.getUsername().trim();
            if (!USERNAME.matcher(name).matches() || !names.add(name)) {
                throw new IllegalStateException("archmorph.security.auth.users[" + i + "].username is missing, invalid "
                        + "(letters, digits and . _ @ - only) or duplicated.");
            }
            String hash = user.getPasswordHash() == null ? "" : user.getPasswordHash().trim();
            if (hash.startsWith("{bcrypt}")) {
                hash = hash.substring("{bcrypt}".length());
            }
            if (!BCRYPT.matcher(hash).matches()) {
                throw new IllegalStateException("archmorph.security.auth.users[" + i + "].password-hash must be a bcrypt "
                        + "hash ($2a$/$2b$/$2y$...); plain-text passwords are not accepted. See docs/DEPLOYMENT.md.");
            }
            users.add(User.withUsername(name).password(hash).roles("USER").build());
        }
        return users;
    }
}
