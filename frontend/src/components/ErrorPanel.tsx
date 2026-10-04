import { AlertOctagon } from 'lucide-react';
import { ApiError } from '../api/client';

/** User-facing error: reason, what to do, request ID. Never shows stack traces or raw server output. */
export function ErrorPanel({ error, title = 'Something went wrong', onRetry }: { error: unknown; title?: string; onRetry?: () => void }) {
  const api = error instanceof ApiError ? error : null;
  const reason = api?.message ?? 'An unexpected error occurred in the browser.';
  return (
    <div role="alert" className="rounded-lg border border-fail/40 bg-fail-soft p-4">
      <div className="flex items-start gap-3">
        <AlertOctagon className="mt-0.5 h-5 w-5 shrink-0 text-fail" aria-hidden />
        <div className="min-w-0 flex-1 text-sm">
          <p className="font-semibold text-fg">{title}</p>
          <p className="mt-2 text-muted">
            <span className="font-medium text-fg">Reason:</span> {reason}
          </p>
          {api?.hint && (
            <p className="mt-1 text-muted">
              <span className="font-medium text-fg">What you can do:</span> {api.hint}
            </p>
          )}
          {api && (
            <p className="mt-2 font-mono text-[11px] text-faint">
              {api.code}
              {api.requestId ? ` · request ${api.requestId}` : ''}
            </p>
          )}
          {onRetry && (
            <button type="button" onClick={onRetry} className="mt-3 text-sm font-medium text-accent hover:underline">
              Try again
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
