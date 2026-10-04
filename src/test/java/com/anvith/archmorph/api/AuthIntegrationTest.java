package com.anvith.archmorph.api;

import com.anvith.archmorph.support.ApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code archmorph.security.auth.mode=BASIC}: login, CSRF header check, per-user projects, login throttling. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "archmorph.workspace.root=target/test-workspace-auth",
        "archmorph.validation.build.enabled=false",
        "archmorph.security.auth.mode=BASIC"
})
class AuthIntegrationTest {

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void passwords(DynamicPropertyRegistry registry) {
        // an indexed list binds from one property source, so names and hashes are registered together
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        registry.add("archmorph.security.auth.users[0].username", () -> "ada");
        registry.add("archmorph.security.auth.users[1].username", () -> "bob");
        registry.add("archmorph.security.auth.users[0].password-hash", () -> "{bcrypt}" + encoder.encode("ada-secret"));
        registry.add("archmorph.security.auth.users[1].password-hash", () -> encoder.encode("bob-secret"));
    }

    @Test
    void requestsWithoutCredentialsGetABasicChallenge() throws Exception {
        ApiClient anonymous = new ApiClient(port);

        HttpResponse<String> response = anonymous.get("/api/v1/jobs/00000000-0000-0000-0000-000000000000");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(v -> assertThat(v).startsWith("Basic"));
        assertThat(anonymous.json(response).get("errorCode").asString()).isEqualTo("UNAUTHORIZED");
        assertThat(anonymous.get("/").statusCode()).as("the UI is protected too").isEqualTo(401);
        assertThat(anonymous.get("/actuator/health").statusCode()).as("health stays public").isEqualTo(200);
    }

    @Test
    void stateChangingRequestsNeedTheUiHeader() throws Exception {
        ApiClient ada = new ApiClient(port).as("ada", "ada-secret");
        ApiClient adaWithoutHeader = new ApiClient(port).withHeaders(java.util.Map.of("Authorization",
                "Basic " + java.util.Base64.getEncoder().encodeToString("ada:ada-secret".getBytes())));

        HttpResponse<String> refused = adaWithoutHeader.upload(ApiClient.zipFixture("simple-layered"), "p.zip");
        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(adaWithoutHeader.get("/api/v1/jobs/00000000-0000-0000-0000-000000000000").statusCode())
                .as("reads do not need it").isEqualTo(404);

        assertThat(ada.upload(ApiClient.zipFixture("simple-layered"), "p.zip").statusCode()).isEqualTo(202);
    }

    @Test
    void projectsAreVisibleOnlyToTheirOwner() throws Exception {
        ApiClient ada = new ApiClient(port).as("ada", "ada-secret");
        ApiClient bob = new ApiClient(port).as("bob", "bob-secret");

        HttpResponse<String> created = ada.upload(ApiClient.zipFixture("simple-layered"), "p.zip");
        assertThat(created.statusCode()).isEqualTo(202);
        String projectId = ada.data(created).get("projectId").asString();
        String jobId = ada.data(created).get("jobId").asString();
        ada.awaitJob(jobId);

        assertThat(ada.get("/api/v1/projects/" + projectId).statusCode()).isEqualTo(200);
        HttpResponse<String> foreign = bob.get("/api/v1/projects/" + projectId);
        assertThat(foreign.statusCode()).as("another user's project does not exist for bob").isEqualTo(404);
        assertThat(bob.get("/api/v1/jobs/" + jobId).statusCode()).isEqualTo(404);
        assertThat(bob.send("DELETE", "/api/v1/projects/" + projectId, null).statusCode()).isEqualTo(404);
        assertThat(ada.get("/api/v1/projects/" + projectId).statusCode()).as("still there").isEqualTo(200);
    }

    @Test
    void repeatedFailedLoginsAreThrottled() throws Exception {
        ApiClient attacker = new ApiClient(port).as("mallory", "guess");
        for (int i = 0; i < 10; i++) {
            assertThat(attacker.get("/api/v1/jobs/00000000-0000-0000-0000-000000000000").statusCode()).isEqualTo(401);
        }
        HttpResponse<String> blocked = attacker.get("/api/v1/jobs/00000000-0000-0000-0000-000000000000");
        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).isPresent();
        assertThat(new ApiClient(port).as("bob", "bob-secret").get("/api/v1/jobs/00000000-0000-0000-0000-000000000000").statusCode())
                .as("other users on the same address are not locked out").isEqualTo(404);
    }
}
