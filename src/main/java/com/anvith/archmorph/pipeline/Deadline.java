package com.anvith.archmorph.pipeline;

import com.anvith.archmorph.common.exception.ArchMorphException;
import com.anvith.archmorph.common.exception.ErrorCode;

import java.time.Duration;

/** Cooperative time limit checked between pipeline phases and per file. */
public final class Deadline {

    private final long expiresAtNanos;
    private final String what;
    private final Duration limit;

    private Deadline(Duration limit, String what) {
        this.limit = limit;
        this.what = what;
        this.expiresAtNanos = System.nanoTime() + limit.toNanos();
    }

    public static Deadline after(Duration limit, String what) {
        return new Deadline(limit, what);
    }

    public static Deadline never() {
        return new Deadline(Duration.ofDays(3650), "operation");
    }

    public void check() {
        if (System.nanoTime() > expiresAtNanos || Thread.currentThread().isInterrupted()) {
            throw new ArchMorphException(ErrorCode.TIMEOUT,
                    "The " + what + " exceeded the maximum duration of " + limit.toSeconds() + " seconds.",
                    "Analyse a smaller project, or raise the limit in the ArchMorph configuration.");
        }
    }
}
