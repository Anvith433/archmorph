package com.anvith.archmorph.api;

import com.anvith.archmorph.support.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end tests of the REST API over real HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "archmorph.workspace.root=target/test-workspace-api",
        "archmorph.validation.build.enabled=false",
        "archmorph.security.rate-limit.enabled=false",
        "archmorph.jobs.max-active-jobs-per-client=20"
})
class ProjectApiIntegrationTest {

    @LocalServerPort
    int port;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
    }

    @Test
    void completeReviewTransformAndDownloadFlow() throws Exception {
        HttpResponse<String> upload = api.upload(ApiClient.zipFixture("spring-layered"), "spring-layered.zip");
        assertThat(upload.statusCode()).isEqualTo(202);
        JsonNode created = api.data(upload);
        String projectId = created.get("projectId").asString();
        assertThat(created.get("status").asString()).isIn("QUEUED", "ANALYZING");

        JsonNode job = api.awaitJob(created.get("jobId").asString());
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("COMPLETED");
        List<String> events = new ArrayList<>();
        job.get("events").forEach(e -> events.add(e.get("event").asString()));
        assertThat(events).containsSubsequence("UPLOAD_COMPLETE", "EXTRACTION_COMPLETE", "PARSING_STARTED", "PARSING_COMPLETE",
                "DEPENDENCY_ANALYSIS_COMPLETE", "MODULE_DISCOVERY_COMPLETE", "PLAN_CREATED");

        String p = "/api/v1/projects/" + projectId;
        JsonNode project = api.data(api.get(p));
        assertThat(project.get("status").asString()).isEqualTo("READY_FOR_REVIEW");
        assertThat(project.get("name").asString()).isEqualTo("spring-layered");
        assertThat(project.get("capabilities").get("canDownload").asBoolean()).isFalse();

        JsonNode analysis = api.data(api.get(p + "/analysis"));
        assertThat(analysis.get("counts").get("controllers").asInt()).isEqualTo(3);
        assertThat(analysis.get("counts").get("businessModules").asInt()).isEqualTo(3);
        assertThat(analysis.get("indicators").get("note").asString()).contains("static-analysis indicators");
        assertThat(analysis.toString()).doesNotContain(System.getProperty("user.dir"));

        JsonNode graph = api.data(api.get(p + "/dependencies"));
        assertThat(graph.get("nodes").size()).isGreaterThan(10);
        assertThat(graph.get("edges").get(0).has("type")).isTrue();
        assertThat(api.data(api.get(p + "/architecture")).get("proposed").get("modules").size()).isEqualTo(3);

        JsonNode modules = api.data(api.get(p + "/modules"));
        assertThat(modules.get("suggestion").toString()).contains("\"name\":\"user\"");

        HttpResponse<String> edited = api.send("PUT", p + "/modules",
                "{\"edits\":[{\"type\":\"RENAME_MODULE\",\"module\":\"user\",\"newName\":\"customer\"}]}");
        assertThat(edited.statusCode()).as(edited.body()).isEqualTo(200);
        JsonNode afterEdit = api.data(edited);
        assertThat(afterEdit.get("decisions").size()).isEqualTo(1);
        assertThat(afterEdit.get("finalModules").toString()).contains("\"name\":\"customer\"");
        assertThat(afterEdit.get("suggestion").toString()).contains("\"name\":\"user\"");

        HttpResponse<String> invalidEdit = api.send("PUT", p + "/modules",
                "{\"edits\":[{\"type\":\"RENAME_MODULE\",\"module\":\"order\",\"newName\":\"Not Valid\"}]}");
        assertThat(invalidEdit.statusCode()).isEqualTo(400);
        assertThat(api.json(invalidEdit).get("errorCode").asString()).isEqualTo("INVALID_MODULE_OPERATION");

        // the default target is the modular monolith; this flow checks the package-by-module layout
        assertThat(api.data(api.get(p + "/plan")).get("strategy").asString()).isEqualTo("MODULAR_MONOLITH");
        HttpResponse<String> badStrategy = api.send("PUT", p + "/strategy", "{\"strategy\":\"MICROSERVICES\"}");
        assertThat(badStrategy.statusCode()).isEqualTo(400);
        ApiClient.assertSafeError(badStrategy);
        HttpResponse<String> strategy = api.send("PUT", p + "/strategy", "{\"strategy\":\"MODULAR_BY_DOMAIN\"}");
        assertThat(strategy.statusCode()).as(strategy.body()).isEqualTo(200);
        assertThat(api.data(strategy).get("strategy").asString()).isEqualTo("MODULAR_BY_DOMAIN");

        JsonNode plan = api.data(api.get(p + "/plan"));
        assertThat(plan.get("classMap").get("com.demo.service.UserService").asString())
                .isEqualTo("com.demo.modules.customer.service.UserService");
        assertThat(plan.toString()).doesNotContain(System.getProperty("user.dir"));

        HttpResponse<String> dryRun = api.send("POST", p + "/transform?dryRun=true", null);
        assertThat(dryRun.statusCode()).isEqualTo(200);
        JsonNode dry = api.data(dryRun);
        assertThat(dry.get("files").size()).isEqualTo(plan.get("entries").size());
        assertThat(api.data(api.get(p)).get("capabilities").get("canDownload").asBoolean()).as("dry run writes nothing").isFalse();

        String movedEntry = null;
        for (JsonNode entry : plan.get("entries")) {
            if (entry.get("className").asString().equals("OrderService")) {
                movedEntry = entry.get("id").asString();
            }
        }
        JsonNode diff = api.data(api.get(p + "/diff/" + movedEntry));
        assertThat(diff.get("before").asString()).contains("package com.demo.service;");
        assertThat(diff.get("after").asString()).contains("package com.demo.modules.order.service;")
                .contains("import com.demo.modules.customer.service.UserService;");
        assertThat(diff.get("unified").asString()).contains("+package com.demo.modules.order.service;");
        HttpResponse<String> traversal = api.get(p + "/diff/../../etc");
        assertThat(traversal.statusCode()).isIn(400, 404);
        ApiClient.assertSafeError(traversal);

        HttpResponse<String> transform = api.send("POST", p + "/transform", null);
        assertThat(transform.statusCode()).isEqualTo(202);
        JsonNode transformJob = api.awaitJob(api.data(transform).get("jobId").asString());
        assertThat(transformJob.get("status").asString()).as(transformJob.toString()).isEqualTo("COMPLETED");

        JsonNode validation = api.data(api.get(p + "/validation"));
        assertThat(validation.get("status").asString()).isIn("PASS", "WARN");
        assertThat(validation.get("levels").size()).isEqualTo(7);

        HttpResponse<byte[]> download = api.getBytes(p + "/download");
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.headers().firstValue("Content-Disposition").orElse(""))
                .isEqualTo("attachment; filename=\"archmorph-spring-layered-transformed.zip\"");
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(download.body()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        assertThat(names).contains("pom.xml", "src/main/java/com/demo/modules/customer/service/UserService.java",
                "src/main/java/com/demo/shared/security/SecurityConfig.java", "src/main/resources/application.properties",
                "src/test/java/com/demo/modules/customer/service/UserServiceTest.java");
        assertThat(names).noneMatch(n -> n.startsWith("/") || n.contains(".."));
        assertThat(names).contains("MODULES.md");

        for (String report : List.of("analysis.json", "modules.json", "transformation-plan.json", "validation.json",
                "analysis.md", "transformation-summary.md", "validation-report.md")) {
            assertThat(api.get(p + "/reports/" + report).statusCode()).as(report).isEqualTo(200);
        }
        assertThat(api.get(p + "/reports/..%2F..%2Fpom.xml").statusCode()).isIn(400, 404);
        assertThat(api.get(p + "/reports/secrets.txt").statusCode()).isEqualTo(404);

        assertThat(api.send("DELETE", p, null).statusCode()).isEqualTo(200);
        assertThat(api.get(p).statusCode()).isEqualTo(404);
    }

    @Test
    void unknownAndMalformedProjectsAreNotFoundWithoutLeakingDetails() throws Exception {
        HttpResponse<String> unknown = api.get("/api/v1/projects/" + UUID.randomUUID());
        assertThat(unknown.statusCode()).isEqualTo(404);
        JsonNode body = api.json(unknown);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("errorCode").asString()).isEqualTo("PROJECT_NOT_FOUND");
        assertThat(body.get("requestId").asString()).isNotBlank();
        assertThat(body.has("timestamp")).isTrue();
        ApiClient.assertSafeError(unknown);

        HttpResponse<String> malformed = api.get("/api/v1/projects/not-a-uuid/analysis");
        assertThat(malformed.statusCode()).isEqualTo(404);
        ApiClient.assertSafeError(malformed);
        assertThat(api.get("/api/v1/jobs/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
    }

    @Test
    void rejectsNonZipUploadsAndUnsafeArchives() throws Exception {
        HttpResponse<String> text = api.upload("hello".getBytes(), "notes.txt");
        assertThat(text.statusCode()).isEqualTo(400);
        assertThat(api.json(text).get("errorCode").asString()).isEqualTo("INVALID_ARCHIVE");

        HttpResponse<String> fake = api.upload("PK not really".getBytes(), "fake.zip");
        assertThat(fake.statusCode()).isEqualTo(400);

        HttpResponse<String> slip = api.upload(ApiClient.zipOf("../../evil.txt", "pwned"), "evil.zip");
        assertThat(slip.statusCode()).isEqualTo(202);
        JsonNode job = api.awaitJob(api.data(slip).get("jobId").asString());
        assertThat(job.get("status").asString()).isEqualTo("FAILED");
        assertThat(job.get("error").get("code").asString()).isEqualTo("UNSAFE_ARCHIVE_ENTRY");
        assertThat(job.get("error").get("hint").asString()).isNotBlank();

        HttpResponse<String> noPom = api.upload(ApiClient.zipOf("readme.md", "# hi"), "empty-project.zip");
        JsonNode noPomJob = api.awaitJob(api.data(noPom).get("jobId").asString());
        assertThat(noPomJob.get("error").get("code").asString()).isEqualTo("INVALID_PROJECT_STRUCTURE");
    }

    @Test
    void securityHeadersAndCorsPolicy() throws Exception {
        HttpResponse<String> response = api.get("/api/v1/projects/" + UUID.randomUUID());
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(response.headers().firstValue("Referrer-Policy")).contains("no-referrer");
        assertThat(response.headers().firstValue("Content-Security-Policy").orElse("")).contains("default-src 'self'")
                .contains("frame-ancestors 'none'");
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(response.headers().firstValue("X-Request-Id")).isPresent();

        HttpResponse<String> allowed = api.options("/api/v1/projects", "http://localhost:5173");
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:5173");
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();

        HttpResponse<String> denied = api.options("/api/v1/projects", "https://evil.example");
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(denied.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void defaultTransformationIsAModularMonolithWithDocumentedModuleApis() throws Exception {
        JsonNode created = api.data(api.upload(ApiClient.zipFixture("spring-layered"), "shop.zip"));
        assertThat(api.awaitJob(created.get("jobId").asString()).get("status").asString()).isEqualTo("COMPLETED");
        String p = "/api/v1/projects/" + created.get("projectId").asString();

        JsonNode plan = api.data(api.get(p + "/plan"));
        assertThat(plan.get("strategy").asString()).isEqualTo("MODULAR_MONOLITH");
        assertThat(plan.get("classMap").get("com.demo.service.UserService").asString()).isEqualTo("com.demo.user.UserService");
        assertThat(plan.get("classMap").get("com.demo.controller.UserController").asString())
                .isEqualTo("com.demo.user.controller.UserController");
        JsonNode proposed = api.data(api.get(p + "/architecture")).get("proposed");
        assertThat(proposed.get("strategy").asString()).isEqualTo("MODULAR_MONOLITH");
        assertThat(proposed.get("modules").toString()).contains("(api)");

        JsonNode job = api.awaitJob(api.data(api.send("POST", p + "/transform", null)).get("jobId").asString());
        assertThat(job.get("status").asString()).as(job.toString()).isEqualTo("COMPLETED");
        JsonNode validation = api.data(api.get(p + "/validation"));
        for (JsonNode level : validation.get("levels")) {
            if (level.get("level").asString().equals("ARCHITECTURE_RULES")) {
                assertThat(level.get("status").asString()).as(level.toString()).isEqualTo("PASS");
            }
        }

        String modulesMd = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(api.getBytes(p + "/download").body()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("MODULES.md")) {
                    modulesMd = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        assertThat(modulesMd).isNotNull()
                .contains("# Modules of shop", "## user", "**Public API** (used by other modules): `User`",
                        "ApplicationModules.of(DemoApplication.class).verify()");
    }
}
