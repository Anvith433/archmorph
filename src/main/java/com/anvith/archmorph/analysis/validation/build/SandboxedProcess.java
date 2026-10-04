package com.anvith.archmorph.analysis.validation.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs one allowlisted build command as a child process: cleared environment, no stdin, hard wall-clock timeout
 * with the whole process tree killed on expiry, output captured up to a limit. Shared by the Maven and Gradle
 * runners so both get the same guarantees.
 */
final class SandboxedProcess {

    record Execution(int exitCode, String stdout, String stderr, long durationMillis, boolean timedOut, boolean truncated) {
    }

    private SandboxedProcess() {
    }

    static Execution execute(List<String> command, Path workingDirectory, Map<String, String> environment, Duration timeout,
                             int maxOutputChars) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(false);
        builder.environment().clear();
        builder.environment().putAll(environment);

        long started = System.nanoTime();
        Process process = builder.start();
        process.getOutputStream().close();

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        AtomicBoolean truncated = new AtomicBoolean();
        Thread outReader = reader(process.getInputStream(), out, maxOutputChars, truncated);
        Thread errReader = reader(process.getErrorStream(), err, maxOutputChars, truncated);

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        try {
            outReader.join(2000);
            errReader.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long millis = (System.nanoTime() - started) / 1_000_000;
        synchronized (out) {
            synchronized (err) {
                return new Execution(finished ? process.exitValue() : -1, out.toString(), err.toString(), millis, !finished,
                        truncated.get());
            }
        }
    }

    /** Replaces absolute paths with placeholders and caps the length. */
    static String mask(String text, Map<String, String> replacements, int maxChars) {
        String result = text;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            String needle = replacement.getKey();
            if (needle != null && needle.length() >= 2) {
                result = result.replace(needle, replacement.getValue());
            }
        }
        return result.length() > maxChars ? result.substring(0, maxChars) : result;
    }

    /** An executable given as an absolute path or found on the server's PATH; arbitrary strings are refused. */
    static Path resolveExecutable(String configured) {
        if (configured == null || configured.isBlank() || !configured.matches("[A-Za-z0-9_./\\\\:-]+")) {
            return null;
        }
        Path candidate = Path.of(configured);
        if (candidate.isAbsolute()) {
            return Files.isExecutable(candidate) ? candidate : null;
        }
        return findOnPath(configured);
    }

    static Path findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String directory : path.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(directory).resolve(name);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** CPU-time and file-size limits through prlimit, when the server has it. */
    static List<String> limits(Duration timeout) {
        Path prlimit = findOnPath("prlimit");
        if (prlimit == null) {
            return List.of();
        }
        long cpuSeconds = Math.max(60, timeout.toSeconds() * 2);
        return List.of(prlimit.toString(), "--cpu=" + cpuSeconds, "--fsize=" + (2L << 30), "--");
    }

    private static Thread reader(InputStream stream, StringBuilder sink, int max, AtomicBoolean truncated) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[8192];
            try (InputStream in = stream) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    synchronized (sink) {
                        if (sink.length() < max) {
                            sink.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                        } else {
                            truncated.set(true);
                        }
                    }
                }
            } catch (IOException ignored) {
                // stream closed by process termination
            }
        }, "archmorph-build-output");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
