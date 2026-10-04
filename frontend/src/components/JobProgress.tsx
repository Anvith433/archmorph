import { Check, Circle, Loader2 } from 'lucide-react';
import type { Job } from '../api/types';
import { cn, titleCase } from '../lib/format';

const ANALYSIS_STEPS = ['UPLOAD_COMPLETE', 'EXTRACTION_COMPLETE', 'PARSING_COMPLETE', 'DEPENDENCY_ANALYSIS_COMPLETE', 'ARCHITECTURE_ANALYSIS_COMPLETE', 'MODULE_DISCOVERY_COMPLETE', 'PLAN_CREATED'];
const TRANSFORM_STEPS = ['PLAN_CREATED', 'TRANSFORMATION_STARTED', 'TRANSFORMATION_COMPLETE', 'VALIDATION_STARTED', 'VALIDATION_COMPLETE'];
const VALIDATE_STEPS = ['VALIDATION_STARTED', 'VALIDATION_COMPLETE'];

/** Step list driven by the structured job events. */
export function JobProgress({ job }: { job: Job }) {
  const steps = job.type === 'ANALYZE' ? ANALYSIS_STEPS : job.type === 'TRANSFORM' ? TRANSFORM_STEPS : VALIDATE_STEPS;
  const seen = new Map(job.events.map((e) => [e.event, e]));
  const running = job.status !== 'COMPLETED' && job.status !== 'FAILED';
  const firstPending = steps.findIndex((s) => !seen.has(s));

  return (
    <ol className="space-y-2" aria-label={`${titleCase(job.type)} progress`}>
      {steps.map((step, index) => {
        const event = seen.get(step);
        const active = running && index === firstPending;
        return (
          <li key={step} className="flex items-start gap-3 text-sm">
            <span className={cn('mt-0.5 flex h-4 w-4 items-center justify-center rounded-full', event ? 'bg-ok text-white' : active ? 'text-info' : 'text-faint')}>
              {event ? <Check className="h-3 w-3" aria-hidden /> : active ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Circle className="h-3 w-3" aria-hidden />}
            </span>
            <div className="min-w-0">
              <p className={cn(event ? 'text-fg' : 'text-muted')}>
                {titleCase(step)}
                <span className="sr-only">{event ? ' (done)' : active ? ' (in progress)' : ' (pending)'}</span>
              </p>
              {event?.detail && <p className="text-xs text-muted">{event.detail}</p>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
