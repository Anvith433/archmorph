package com.anvith.archmorph.security;

import com.anvith.archmorph.api.web.ClientResolver;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/**
 * HTTP security boundary, deliberately separate from the analysis code.
 *
 * <p><b>Local no-login mode</b> ({@code archmorph.security.auth.mode=NONE}, the default): every request is
 * permitted; there are no sessions and no cookies, hence no CSRF token (the API is stateless).</p>
 *
 * <p><b>Basic mode</b> ({@code BASIC}): every request except the health check needs HTTP Basic credentials of a
 * configured user, checked against a bcrypt hash; still stateless (no session, no cookie). Because browsers resend
 * Basic credentials on their own, state-changing API requests must also carry {@code X-Requested-With}, and
 * failed logins are throttled per client; see {@link AuthGuards}. Projects are visible only to the user who
 * uploaded them ({@code OwnerAccessPolicy}). Nothing in the analysis packages depends on this class.</p>
 *
 * <p>CORS never uses {@code *}: only the configured origins are allowed and credentials are not.</p>
 */
@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebSecurityConfiguration {

    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; "
            + "frame-ancestors 'none'; base-uri 'none'; form-action 'self'";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ArchMorphProperties properties, ClientResolver clients,
                                            JsonMapper json, UserDetailsService users, PasswordEncoder passwords)
            throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissions -> permissions.policy(
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .cacheControl(Customizer.withDefaults()));
        if (properties.getSecurity().getAuth().getMode() == ArchMorphProperties.AuthMode.BASIC) {
            AuthGuards.LoginThrottle throttle = new AuthGuards.LoginThrottle();
            AuthGuards.EntryPoint entryPoint = new AuthGuards.EntryPoint(throttle, clients, json);
            DaoAuthenticationProvider bcrypt = new DaoAuthenticationProvider(users);
            bcrypt.setPasswordEncoder(passwords);
            http
                    .authenticationManager(new ProviderManager(new CachingAuthenticationProvider(bcrypt)))
                    .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
                    .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                    .addFilterBefore(new AuthGuards.LoginThrottleFilter(throttle, clients, json), BasicAuthenticationFilter.class)
                    .addFilterBefore(new AuthGuards.RequiredHeaderFilter(json), BasicAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                            .anyRequest().authenticated());
        } else {
            http
                    .httpBasic(basic -> basic.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }

    CorsConfigurationSource corsConfigurationSource(ArchMorphProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = properties.getSecurity().getAllowedOrigins().stream()
                .filter(origin -> !origin.isBlank() && !origin.trim().equals("*")).toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Content-Disposition", "Retry-After"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /**
     * Users of {@code BASIC} mode, from configuration. In local mode no users exist; defining this bean also stops
     * Spring Boot from creating a default user with a generated password that would be logged at startup.
     */
    @Bean
    UserDetailsService users(ArchMorphProperties properties) {
        ArchMorphProperties.Auth auth = properties.getSecurity().getAuth();
        if (auth.getMode() != ArchMorphProperties.AuthMode.BASIC) {
            return username -> {
                throw new UsernameNotFoundException("Authentication is not enabled");
            };
        }
        return new InMemoryUserDetailsManager(ConfiguredUsers.of(auth.getUsers()));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
