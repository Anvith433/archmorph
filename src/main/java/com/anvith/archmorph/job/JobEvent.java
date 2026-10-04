package com.anvith.archmorph.job;

import com.anvith.archmorph.pipeline.ProgressEvent;

import java.time.Instant;

/** One structured progress event of a job. */
public record JobEvent(Instant timestamp, ProgressEvent event, String detail) {
}
