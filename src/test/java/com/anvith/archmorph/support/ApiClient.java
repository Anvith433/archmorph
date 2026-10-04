package com.anvith.archmorph.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Minimal HTTP client for API tests. */
public final class ApiClient {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String base;

    public ApiClient(int port) {
        this.base = "http://localhost:" + port;
    }

    public HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<byte[]> getBytes(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    public HttpResponse<String> send(String method, String path, String jsonBody) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, jsonBody == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(jsonBody));
        if (jsonBody != null) {
            request.header("Content-Type", "application/json");
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> options(String path, String origin) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST").build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> upload(byte[] content, String fileName) throws Exception {
        String boundary = "----archmorph" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
                + "\"\r\nContent-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/projects"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    public JsonNode json(HttpResponse<String> response) {
        return json.readTree(response.body());
    }

    public JsonNode data(HttpResponse<String> response) {
        return json(response).get("data");
    }

    /** Poll a job until it reaches a terminal state. */
    public JsonNode awaitJob(String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode job = data(get("/api/v1/jobs/" + jobId));
            String status = job.get("status").asString();
            if (status.equals("COMPLETED") || status.equals("FAILED")) {
                return job;
            }
            Thread.sleep(150);
        }
        throw new AssertionError("job did not finish: " + jobId);
    }

    /** ZIP a fixture directory (as a user would) with a top-level folder. */
    public static byte[] zipFixture(String fixture) throws IOException {
        Path root = Fixtures.path(fixture);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes); Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (relative.equals("expected.json")) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(fixture + "/" + relative));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    public static byte[] zipOf(String name, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    public static void assertSafeError(HttpResponse<String> response) {
        assertThat(response.body()).doesNotContain("/home/", "/tmp/", "at com.", "at org.", "Exception:", "java.lang.");
    }
}
