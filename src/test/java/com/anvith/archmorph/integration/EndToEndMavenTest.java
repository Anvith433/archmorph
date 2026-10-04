package com.anvith.archmorph.integration;

import com.anvith.archmorph.support.ApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The definition-of-done scenario: a layered Spring Boot project is uploaded, analysed, reviewed
 * (one module edit), transformed, re-parsed, validated with an allowlisted, sandboxed Maven build and
 * downloaded. Maven runs offline against the local repository populated by this project's own build.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "archmorph.workspace.root=target/test-workspace-e2e",
        "archmorph.validation.build.enabled=true",
        "archmorph.validation.build.offline=true",
        "archmorph.validation.build.local-repository=${user.home}/.m2/repository",
        "archmorph.security.rate-limit.enabled=false"
})
class EndToEndMavenTest {

    @LocalServerPort
    int port;

    @Test
    void layeredSpringBootProjectBecomesAValidatedModularMonolith() throws Exception {
        assumeTrue(mavenOnPath(), "Maven is not installed; the end-to-end build check is skipped");
        ApiClient api = new ApiClient(port);

        JsonNode created = api.data(api.upload(ApiClient.zipFixture("spring-layered"), "shop.zip"));
        assertThat(api.awaitJob(created.get("jobId").asString()).get("status").asString()).isEqualTo("COMPLETED");
        String p = "/api/v1/projects/" + created.get("projectId").asString();

        JsonNode modules = api.data(api.get(p + "/modules"));
        assertThat(modules.get("finalModules").toString()).contains("\"name\":\"user\"", "\"name\":\"order\"", "\"name\":\"payment\"");
        // the user reviews the suggestion and locks a class (no structural change)
        assertThat(api.send("PUT", p + "/modules",
                "{\"edits\":[{\"type\":\"LOCK_CLASS\",\"className\":\"com.demo.service.UserService\"}]}").statusCode()).isEqualTo(200);

        JsonNode job = api.awaitJob(api.data(api.send("POST", p + "/transform", null)).get("jobId").asString());
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("COMPLETED");

        JsonNode validation = api.data(api.get(p + "/validation"));
        for (JsonNode level : validation.get("levels")) {
            String name = level.get("level").asString();
            String status = level.get("status").asString();
            if (name.equals("ARCHITECTURE_RULES")) {
                assertThat(status).isIn("PASS", "WARN");
            } else {
                assertThat(status).as(name + ": " + level).isEqualTo("PASS");
            }
        }
        assertThat(validation.get("build").get("exitCode").asInt()).isZero();
        assertThat(validation.get("build").get("command").toString()).contains("test-compile");
        assertThat(api.getBytes(p + "/download").statusCode()).isEqualTo(200);
    }

    private static boolean mavenOnPath() {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (Files.isExecutable(Path.of(dir, "mvn"))) {
                return true;
            }
        }
        return false;
    }
}
