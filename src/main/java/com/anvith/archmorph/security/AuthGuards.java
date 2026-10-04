package com.anvith.archmorph.security;

import com.anvith.archmorph.api.web.ClientResolver;
import com.anvith.archmorph.common.exception.ErrorCode;
import com.anvith.archmorph.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Request guards used when authentication is enabled ({@code archmorph.security.auth.mode=BASIC}).
 *
 * <ul>
 *   <li><b>Custom-header check (CSRF).</b> Browsers resend cached Basic credentials automatically, also on
 *       requests a hostile page triggers. A cross-site form or image cannot set a custom header, and a cross-site
 *       script that tries is stopped by the CORS preflight; so state-changing API requests must carry
 *       {@code X-Requested-With}, which the UI always sends.</li>
 *   <li><b>Login throttling.</b> After {@value #MAX_FAILURES} failed logins for one user name from one client, or
 *       {@value #MAX_CLIENT_FAILURES} for any names from one client, within {@link #FAILURE_WINDOW}, further
 *       attempts are refused with 429 until the window passes.</li>
 *   <li><b>Entry point.</b> 401 with a {@code WWW-Authenticate} challenge and the usual JSON envelope.</li>
 * </ul>
 */
final class AuthGuards {

    static final int MAX_FAILURES = 10;
    static final int MAX_CLIENT_FAILURES = 50;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(5);

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private AuthGuards() {
    }

    /** Refuses state-changing API requests without {@code X-Requested-With}. */
    static final class RequiredHeaderFilter extends OncePerRequestFilter {

        private final JsonMapper json;

        RequiredHeaderFilter(JsonMapper json) {
            this.json = json;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return SAFE_METHODS.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String header = request.getHeader("X-Requested-With");
            if (header == null || header.isBlank()) {
                write(json, response, 403, ErrorCode.INVALID_REQUEST.name(), "The request was refused.",
                        "State-changing requests must be sent by the ArchMorph UI or carry the X-Requested-With header.");
                return;
            }
            chain.doFilter(request, response);
        }
    }

    /** Counts failed logins per client and per (client, user name); see {@link AuthGuards}. */
    static final class LoginThrottle {

        private record Failures(long start, int count) {
        }

        private final ConcurrentMap<String, Failures> failures = new ConcurrentHashMap<>();

        boolean blocked(String client, String username) {
            return count(client + "|" + username) >= MAX_FAILURES || count(client) >= MAX_CLIENT_FAILURES;
        }

        void recordFailure(String client, String username) {
            increment(client + "|" + username);
            increment(client);
            if (failures.size() > 10_000) {
                failures.values().removeIf(this::expired);
            }
        }

        private int count(String key) {
            Failures entry = failures.get(key);
            return entry == null || expired(entry) ? 0 : entry.count();
        }

        private void increment(String key) {
            failures.compute(key, (k, entry) -> entry == null || expired(entry)
                    ? new Failures(System.currentTimeMillis(), 1)
                    : new Failures(entry.start(), entry.count() + 1));
        }

        private boolean expired(Failures entry) {
            return System.currentTimeMillis() - entry.start() >= FAILURE_WINDOW.toMillis();
        }
    }

    /** User name of a Basic {@code Authorization} header, hashed; "" when absent or malformed. */
    static String attemptedUser(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return "";
        }
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            return Integer.toHexString((colon < 0 ? decoded : decoded.substring(0, colon)).hashCode());
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** Refuses requests with credentials from a client that failed too often. */
    static final class LoginThrottleFilter extends OncePerRequestFilter {

        private final LoginThrottle throttle;
        private final ClientResolver clients;
        private final JsonMapper json;

        LoginThrottleFilter(LoginThrottle throttle, ClientResolver clients, JsonMapper json) {
            this.throttle = throttle;
            this.clients = clients;
            this.json = json;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (request.getHeader(HttpHeaders.AUTHORIZATION) != null && throttle.blocked(clients.networkClient(request), attemptedUser(request))) {
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(FAILURE_WINDOW.toSeconds()));
                write(json, response, 429, ErrorCode.RATE_LIMITED.name(), "Too many failed logins.",
                        "Wait a few minutes before trying again.");
                return;
            }
            chain.doFilter(request, response);
        }
    }

    /** 401 with a Basic challenge; failed credentials count towards the login throttle. */
    static final class EntryPoint implements AuthenticationEntryPoint {

        private final LoginThrottle throttle;
        private final ClientResolver clients;
        private final JsonMapper json;

        EntryPoint(LoginThrottle throttle, ClientResolver clients, JsonMapper json) {
            this.throttle = throttle;
            this.clients = clients;
            this.json = json;
        }

        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
                throws IOException {
            if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
                throttle.recordFailure(clients.networkClient(request), attemptedUser(request));
            }
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"ArchMorph\", charset=\"UTF-8\"");
            write(json, response, 401, "UNAUTHORIZED", "Authentication required.", "Sign in with your ArchMorph account.");
        }
    }

    private static void write(JsonMapper json, HttpServletResponse response, int status, String code, String message,
                              String hint) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json.writeValueAsString(ApiResponse.error(code, message, hint)));
    }
}
