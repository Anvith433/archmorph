import { AlertTriangle, CheckCircle2, CircleDashed, Loader2, MinusCircle, XCircle } from 'lucide-react';
import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { cn } from '../lib/format';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';

export function Button({
  variant = 'secondary',
  size = 'md',
  className,
  children,
  busy,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: 'sm' | 'md'; busy?: boolean }) {
  return (
    <button
      type="button"
      {...props}
      disabled={props.disabled || busy}
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-md font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50',
        size === 'sm' ? 'h-7 px-2.5 text-xs' : 'h-9 px-3.5 text-sm',
        variant === 'primary' && 'bg-accent-strong text-white hover:bg-accent',
        variant === 'secondary' && 'border border-line-strong bg-panel text-fg hover:bg-panel-hover',
        variant === 'ghost' && 'text-muted hover:bg-panel-hover hover:text-fg',
        variant === 'danger' && 'border border-fail/40 bg-fail-soft text-fail hover:bg-fail/20',
        className,
      )}
    >
      {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}
      {children}
    </button>
  );
}

export function Panel({
  title,
  subtitle,
  actions,
  children,
  className,
  padded = true,
}: {
  title?: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  padded?: boolean;
}) {
  return (
    <section className={cn('rounded-lg border border-line bg-panel', className)}>
      {(title || actions) && (
        <header className="flex flex-wrap items-start justify-between gap-3 border-b border-line px-4 py-3">
          <div>
            {title && <h2 className="text-sm font-semibold text-fg">{title}</h2>}
            {subtitle && <p className="mt-0.5 text-xs text-muted">{subtitle}</p>}
          </div>
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </header>
      )}
      <div className={padded ? 'p-4' : undefined}>{children}</div>
    </section>
  );
}

export type Tone = 'ok' | 'warn' | 'fail' | 'info' | 'neutral' | 'accent';

const TONE: Record<Tone, string> = {
  ok: 'bg-ok-soft text-ok border-ok/30',
  warn: 'bg-warn-soft text-warn border-warn/30',
  fail: 'bg-fail-soft text-fail border-fail/30',
  info: 'bg-info-soft text-info border-info/30',
  accent: 'bg-accent-soft text-accent border-accent/30',
  neutral: 'bg-sunken text-muted border-line',
};

export function Badge({ tone = 'neutral', children, className, title }: { tone?: Tone; children: ReactNode; className?: string; title?: string }) {
  return (
    <span title={title} className={cn('inline-flex items-center gap-1 rounded border px-1.5 py-0.5 text-[11px] font-medium leading-none whitespace-nowrap', TONE[tone], className)}>
      {children}
    </span>
  );
}

const STATUS: Record<string, { tone: Tone; icon: typeof CheckCircle2; label: string }> = {
  PASS: { tone: 'ok', icon: CheckCircle2, label: 'Pass' },
  WARN: { tone: 'warn', icon: AlertTriangle, label: 'Warn' },
  FAIL: { tone: 'fail', icon: XCircle, label: 'Fail' },
  SKIPPED: { tone: 'neutral', icon: MinusCircle, label: 'Skipped' },
  SAFE: { tone: 'ok', icon: CheckCircle2, label: 'Safe' },
  SAFE_WITH_WARNING: { tone: 'warn', icon: AlertTriangle, label: 'Safe with warning' },
  MANUAL_REVIEW: { tone: 'fail', icon: AlertTriangle, label: 'Manual review' },
  UNSUPPORTED: { tone: 'fail', icon: XCircle, label: 'Unsupported' },
  LOW: { tone: 'ok', icon: CircleDashed, label: 'Low' },
  MEDIUM: { tone: 'warn', icon: AlertTriangle, label: 'Medium' },
  HIGH: { tone: 'fail', icon: XCircle, label: 'High' },
  COMPLETED: { tone: 'ok', icon: CheckCircle2, label: 'Completed' },
  FAILED: { tone: 'fail', icon: XCircle, label: 'Failed' },
  READY_FOR_REVIEW: { tone: 'accent', icon: CircleDashed, label: 'Ready for review' },
  QUEUED: { tone: 'info', icon: Loader2, label: 'Queued' },
  ANALYZING: { tone: 'info', icon: Loader2, label: 'Analyzing' },
  PLANNING: { tone: 'info', icon: Loader2, label: 'Planning' },
  TRANSFORMING: { tone: 'info', icon: Loader2, label: 'Transforming' },
  VALIDATING: { tone: 'info', icon: Loader2, label: 'Validating' },
};

/** Status with icon and text, so information is never conveyed by colour alone. */
export function StatusBadge({ status, className }: { status: string; className?: string }) {
  const spec = STATUS[status] ?? { tone: 'neutral' as Tone, icon: CircleDashed, label: status };
  const Icon = spec.icon;
  const spinning = spec.icon === Loader2;
  return (
    <Badge tone={spec.tone} className={className}>
      <Icon className={cn('h-3 w-3', spinning && 'animate-spin')} aria-hidden />
      {spec.label}
    </Badge>
  );
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center gap-2 text-sm text-muted">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
      {label}…
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded bg-panel-hover', className)} aria-hidden />;
}

export function EmptyState({ title, children, icon }: { title: string; children?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-line px-6 py-12 text-center">
      {icon && <div className="mb-3 text-faint">{icon}</div>}
      <p className="text-sm font-medium text-fg">{title}</p>
      {children && <div className="mt-1 max-w-md text-sm text-muted">{children}</div>}
    </div>
  );
}

/** Horizontal bar for a 0..1 value with the value printed next to it. */
export function Meter({ value, label, tone }: { value: number; label?: string; tone?: Tone }) {
  const clamped = Math.max(0, Math.min(1, value));
  const auto: Tone = clamped >= 0.75 ? 'ok' : clamped >= 0.5 ? 'warn' : 'fail';
  const color = { ok: 'bg-ok', warn: 'bg-warn', fail: 'bg-fail', info: 'bg-info', accent: 'bg-accent', neutral: 'bg-faint' }[tone ?? auto];
  return (
    <div className="flex items-center gap-2" aria-label={label ? `${label}: ${Math.round(clamped * 100)}%` : undefined}>
      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-sunken">
        <div className={cn('h-full rounded-full', color)} style={{ width: `${clamped * 100}%` }} />
      </div>
      <span className="w-9 text-right font-mono text-xs tabular-nums text-muted">{Math.round(clamped * 100)}%</span>
    </div>
  );
}

export function Code({ children }: { children: ReactNode }) {
  return <code className="rounded bg-sunken px-1 py-0.5 font-mono text-[12px] text-fg">{children}</code>;
}
