package com.anvith.archmorph.pipeline;

/** Receives pipeline progress. The detail text must never contain absolute paths or source code. */
@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = (event, detail) -> {
    };

    void onEvent(ProgressEvent event, String detail);
}
