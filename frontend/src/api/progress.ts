import { api } from './client';
import type { Job } from './types';

/**
 * Source of job progress updates. Polling today; a Server-Sent-Events or WebSocket implementation can
 * replace it without touching the UI.
 */
export interface ProgressSource {
  subscribe(jobId: string, onUpdate: (job: Job) => void, onError: (error: unknown) => void): () => void;
}

export class PollingProgressSource implements ProgressSource {
  constructor(
    private readonly intervalMs = 1200,
    private readonly maxConsecutiveErrors = 5,
  ) {}

  subscribe(jobId: string, onUpdate: (job: Job) => void, onError: (error: unknown) => void): () => void {
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let failures = 0;
    const tick = async () => {
      try {
        const job = await api.job(jobId);
        if (!active) {
          return;
        }
        failures = 0;
        onUpdate(job);
        if (job.status !== 'COMPLETED' && job.status !== 'FAILED') {
          timer = setTimeout(tick, this.intervalMs);
        }
      } catch (error) {
        if (!active) {
          return;
        }
        // Transient failures (server restart, flaky network) back off and retry before giving up.
        failures++;
        if (failures >= this.maxConsecutiveErrors) {
          onError(error);
        } else {
          timer = setTimeout(tick, this.intervalMs * 2 ** failures);
        }
      }
    };
    void tick();
    return () => {
      active = false;
      if (timer) {
        clearTimeout(timer);
      }
    };
  }
}

export const progressSource: ProgressSource = new PollingProgressSource();
