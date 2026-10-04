package com.anvith.archmorph.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static RateLimitFilter.Category categorize(String method, String path) {
        return RateLimitFilter.categorize(new MockHttpServletRequest(method, path));
    }

    @Test
    void expensiveEndpointsHaveTheirOwnBudgets() {
        assertThat(categorize("POST", "/api/v1/projects")).isEqualTo(RateLimitFilter.Category.UPLOAD);
        assertThat(categorize("POST", "/api/v1/projects/x/transform")).isEqualTo(RateLimitFilter.Category.EXPENSIVE);
        assertThat(categorize("POST", "/api/v1/projects/x/validate")).isEqualTo(RateLimitFilter.Category.EXPENSIVE);
        assertThat(categorize("PUT", "/api/v1/projects/x/modules")).isEqualTo(RateLimitFilter.Category.EXPENSIVE);
        assertThat(categorize("GET", "/api/v1/projects/x/download")).isEqualTo(RateLimitFilter.Category.DOWNLOAD);
        assertThat(categorize("GET", "/api/v1/projects/x/reports/analysis.json")).isEqualTo(RateLimitFilter.Category.DOWNLOAD);
        assertThat(categorize("GET", "/api/v1/projects/x")).isEqualTo(RateLimitFilter.Category.GENERAL);
    }
}
