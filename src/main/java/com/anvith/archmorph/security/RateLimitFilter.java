package com.anvith.archmorph.security;

import com.anvith.archmorph.api.web.ClientResolver;
import com.anvith.archmorph.common.config.ArchMorphProperties;
import com.anvith.archmorph.common.exception.ErrorCode;
import com.anvith.archmorph.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-client fixed-window rate limiting with separate budgets for the expensive endpoints
 * (upload, analysis, transformation, validation, download). In-memory and per-instance; put a
 * reverse proxy or gateway in front for multi-instance deployments.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    enum Category {
        UPLOAD,
        EXPENSIVE,
        DOWNLOAD,
        GENERAL
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();

        Window(long start) {
            this.start = start;
        }
    }

    private final ArchMorphProperties properties;
    private final ClientResolver clients;
    private final JsonMapper json;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger sweepCounter = new AtomicInteger();

    public RateLimitFilter(ArchMorphProperties properties, ClientResolver clients, JsonMapper json) {
        this.properties = properties;
        this.clients = clients;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.getSecurity().getRateLimit().isEnabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        ArchMorphProperties.RateLimit limits = properties.getSecurity().getRateLimit();
        Category category = categorize(request);
        int limit = switch (category) {
            case UPLOAD -> limits.getUploadsPerWindow();
            case EXPENSIVE -> limits.getExpensivePerWindow();
            case DOWNLOAD -> limits.getDownloadsPerWindow();
            case GENERAL -> limits.getGeneralPerWindow();
        };
        long windowMillis = limits.getWindow().toMillis();
        long now = System.currentTimeMillis();
        String key = clients.resolve(request) + "|" + category;

        Window window = windows.compute(key, (k, existing) ->
                existing == null || now - existing.start >= windowMillis ? new Window(now) : existing);
        if (window.count.incrementAndGet() > limit) {
            long retryAfter = Math.max(1, (window.start + windowMillis - now + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(json.writeValueAsString(ApiResponse.error(ErrorCode.RATE_LIMITED.name(),
                    "Too many requests. Please slow down.", "Retry in " + retryAfter + " seconds.")));
            return;
        }
        if (sweepCounter.incrementAndGet() % 500 == 0) {
            windows.values().removeIf(w -> now - w.start >= windowMillis * 2);
        }
        chain.doFilter(request, response);
    }

    static Category categorize(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equals(method) && (path.equals("/api/v1/projects") || path.equals("/api/v1/projects/"))) {
            return Category.UPLOAD;
        }
        if ("POST".equals(method) && (path.endsWith("/transform") || path.endsWith("/analyze") || path.endsWith("/validate"))) {
            return Category.EXPENSIVE;
        }
        if ("PUT".equals(method) && path.endsWith("/modules")) {
            return Category.EXPENSIVE;
        }
        if ("GET".equals(method) && (path.endsWith("/download") || path.contains("/reports/"))) {
            return Category.DOWNLOAD;
        }
        return Category.GENERAL;
    }
}
