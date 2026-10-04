package com.anvith.archmorph.api;

import com.anvith.archmorph.support.ApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "archmorph.workspace.root=target/test-workspace-ratelimit",
        "archmorph.security.rate-limit.uploads-per-window=2",
        "archmorph.security.rate-limit.general-per-window=1000"
})
class RateLimitIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void uploadsAreLimitedPerClient() throws Exception {
        ApiClient api = new ApiClient(port);
        byte[] notZip = "x".getBytes();
        assertThat(api.upload(notZip, "a.txt").statusCode()).isEqualTo(400);
        assertThat(api.upload(notZip, "b.txt").statusCode()).isEqualTo(400);

        HttpResponse<String> limited = api.upload(notZip, "c.txt");
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
        assertThat(api.json(limited).get("errorCode").asString()).isEqualTo("RATE_LIMITED");

        assertThat(api.get("/api/v1/jobs/00000000-0000-0000-0000-000000000000").statusCode())
                .as("other endpoints keep their own budget").isEqualTo(404);
    }
}
