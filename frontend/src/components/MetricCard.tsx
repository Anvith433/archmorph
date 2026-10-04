import type { ReactNode } from 'react';
import { cn } from '../lib/format';

export function MetricCard({ label, value, hint, tone }: { label: string; value: ReactNode; hint?: string; tone?: 'warn' | 'fail' }) {
  return (
    <div className="rounded-lg border border-line bg-panel px-4 py-3">
      <p className="text-xs text-muted">{label}</p>
      <p className={cn('mt-1 font-mono text-2xl font-semibold tabular-nums text-fg', tone === 'warn' && 'text-warn', tone === 'fail' && 'text-fail')}>{value}</p>
      {hint && <p className="mt-0.5 text-[11px] text-faint">{hint}</p>}
    </div>
  );
}

/** A 0–100 static-analysis indicator, labelled as such. */
export function Indicator({ label, value, description }: { label: string; value: number; description: string }) {
  const tone = value >= 75 ? 'bg-ok' : value >= 50 ? 'bg-warn' : 'bg-fail';
  const word = value >= 75 ? 'good' : value >= 50 ? 'moderate' : 'low';
  return (
    <div className="rounded-lg border border-line bg-panel p-4">
      <div className="flex items-baseline justify-between">
        <p className="text-sm font-medium text-fg">{label}</p>
        <p className="font-mono text-lg tabular-nums text-fg">
          {value}
          <span className="text-xs text-faint">/100</span>
        </p>
      </div>
      <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-sunken" aria-label={`${label}: ${value} out of 100 (${word})`} role="img">
        <div className={cn('h-full rounded-full', tone)} style={{ width: `${value}%` }} />
      </div>
      <p className="mt-2 text-xs text-muted">{description}</p>
    </div>
  );
}
