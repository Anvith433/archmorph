import { FlaskConical, Play } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { DryRun, PlanEntry, TargetStrategy } from '../api/types';
import { ErrorPanel } from '../components/ErrorPanel';
import { MetricCard } from '../components/MetricCard';
import { primaryAction, TransformationTable } from '../components/TransformationTable';
import { Badge, Button, Code, Panel, Skeleton } from '../components/ui';
import { useProject } from '../hooks/ProjectContext';
import { useResource } from '../hooks/useResource';
import { cn, titleCase } from '../lib/format';

const FILTERS = ['ALL', 'MOVE', 'KEEP', 'MANUAL_REVIEW', 'EXCLUDE', 'HIGH_RISK'] as const;
type Filter = (typeof FILTERS)[number];

function matches(entry: PlanEntry, filter: Filter): boolean {
  if (filter === 'ALL') return true;
  if (filter === 'HIGH_RISK') return entry.risk === 'HIGH';
  if (filter === 'MANUAL_REVIEW') return entry.safety === 'MANUAL_REVIEW' || entry.safety === 'UNSUPPORTED';
  return primaryAction(entry) === filter;
}

export function PlanPage() {
  const { projectId, version, project, trackJob, refresh } = useProject();
  const plan = useResource(() => api.plan(projectId), `${projectId}:${version}`);
  const navigate = useNavigate();
  const [filter, setFilter] = useState<Filter>('ALL');
  const [query, setQuery] = useState('');
  const [dryRun, setDryRun] = useState<DryRun>();
  const [busy, setBusy] = useState<'dry' | 'transform' | 'strategy' | null>(null);
  const [pendingStrategy, setPendingStrategy] = useState<TargetStrategy | null>(null);
  const [error, setError] = useState<unknown>();

  const entries = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (plan.data?.entries ?? []).filter((e) => matches(e, filter) && (!q || e.sourcePath.toLowerCase().includes(q) || e.targetPackage.toLowerCase().includes(q)));
  }, [plan.data, filter, query]);

  const runDry = async () => {
    setBusy('dry');
    setError(undefined);
    try {
      setDryRun(await api.dryRun(projectId));
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  const chooseStrategy = async (strategy: TargetStrategy) => {
    setPendingStrategy(strategy);
    setBusy('strategy');
    setError(undefined);
    setDryRun(undefined);
    try {
      await api.changeStrategy(projectId, strategy);
      refresh();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
      setPendingStrategy(null);
    }
  };

  const transform = async () => {
    setBusy('transform');
    setError(undefined);
    try {
      const created = await api.transform(projectId);
      trackJob(created.jobId);
      navigate('../validation');
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  };

  if (plan.error !== undefined) return <ErrorPanel error={plan.error} onRetry={plan.reload} />;
  if (!plan.data) return <Skeleton className="h-[480px]" />;
  const p = plan.data;
  const canTransform = Boolean(project?.capabilities.canTransform);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-fg">Transformation plan</h1>
          <p className="text-sm text-muted">
            {titleCase(p.strategy)} · base package <Code>{p.basePackage || '(default)'}</Code> · fingerprint <span className="font-mono text-xs text-faint">{p.fingerprint.slice(0, 12)}</span>
          </p>
        </div>
        <div className="flex gap-2">
          <Button onClick={runDry} busy={busy === 'dry'} disabled={busy !== null}>
            <FlaskConical className="h-4 w-4" aria-hidden /> Dry run
          </Button>
          <Button variant="primary" onClick={transform} busy={busy === 'transform'} disabled={busy !== null || !canTransform}>
            <Play className="h-4 w-4" aria-hidden /> Transform &amp; validate
          </Button>
        </div>
      </div>

      {error !== undefined && <ErrorPanel error={error} />}

      <StrategyPicker value={pendingStrategy ?? p.strategy} disabled={busy !== null || !canTransform} onChange={chooseStrategy} />

      {project?.capabilities.manualReviewRequired && (
        <div role="note" className="rounded-lg border border-warn/40 bg-warn-soft px-4 py-3 text-sm text-fg">
          <strong>Manual review required.</strong> Some files stay where they are because moving them automatically is not safe. The rest of the
          plan can still be applied; the reasons are listed per file.
        </div>
      )}

      <section aria-label="Plan summary" className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-8">
        <MetricCard label="Files" value={p.summary.files} />
        <MetricCard label="Moved" value={p.summary.moved} />
        <MetricCard label="Kept" value={p.summary.kept} />
        <MetricCard label="Excluded" value={p.summary.excluded} />
        <MetricCard label="Safe" value={p.summary.safe} />
        <MetricCard label="With warning" value={p.summary.safeWithWarning} tone={p.summary.safeWithWarning ? 'warn' : undefined} />
        <MetricCard label="Manual review" value={p.summary.manualReview + p.summary.unsupported} tone={p.summary.manualReview + p.summary.unsupported ? 'fail' : undefined} />
        <MetricCard label="Conflicts" value={p.summary.conflicts} tone={p.summary.conflicts ? 'fail' : undefined} />
      </section>

      {dryRun && (
        <Panel title="Dry run" subtitle={`All files rewritten in memory in ${dryRun.durationMillis} ms. Nothing was written to disk.`}>
          <div className="grid grid-cols-1 gap-3 text-sm sm:grid-cols-4">
            <div><p className="text-xs text-muted">Files changed</p><p className="font-mono text-fg">{dryRun.files.filter((f) => f.changed).length} / {dryRun.files.length}</p></div>
            <div><p className="text-xs text-muted">Package declarations</p><p className="font-mono text-fg">{dryRun.files.filter((f) => f.packageChanged).length}</p></div>
            <div><p className="text-xs text-muted">Import changes</p><p className="font-mono text-fg">{dryRun.files.reduce((n, f) => n + f.importChanges.length, 0)}</p></div>
            <div><p className="text-xs text-muted">Qualified references</p><p className="font-mono text-fg">{dryRun.files.reduce((n, f) => n + f.qualifiedRewrites, 0)}</p></div>
          </div>
          {dryRun.warnings.length > 0 && <ul className="mt-3 list-disc space-y-0.5 pl-5 text-xs text-warn">{dryRun.warnings.map((w) => <li key={w}>{w}</li>)}</ul>}
        </Panel>
      )}

      {(p.conflicts.length > 0 || p.warnings.length > 0 || p.resourceFindings.length > 0) && (
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
          {p.conflicts.length > 0 && (
            <Panel title="Conflicts" subtitle="Resolved by keeping the files in place for manual review.">
              <ul className="space-y-2 text-xs">
                {p.conflicts.map((c) => (
                  <li key={`${c.type}-${c.target}`} className="rounded border border-line bg-sunken p-2">
                    <Badge tone="fail">{c.type.toLowerCase().replace(/_/g, ' ')}</Badge>
                    <p className="mt-1 break-all font-mono text-fg">{c.target}</p>
                    <ul className="font-mono text-muted">{c.sources.map((s) => <li key={s}>← {s}</li>)}</ul>
                    <p className="mt-1 text-muted">{c.resolution}</p>
                  </li>
                ))}
              </ul>
            </Panel>
          )}
          {p.warnings.length > 0 && (
            <Panel title="Warnings">
              <ul className="max-h-72 space-y-1 overflow-y-auto text-xs text-muted">{p.warnings.map((w) => <li key={w}>{w}</li>)}</ul>
            </Panel>
          )}
          {p.resourceFindings.length > 0 && (
            <Panel title="Resource references" subtitle="Class or package names in configuration files. Not rewritten — check them.">
              <ul className="max-h-72 space-y-2 overflow-y-auto text-xs">
                {p.resourceFindings.map((r) => (
                  <li key={`${r.file}:${r.line}:${r.reference}`}>
                    <p className="font-mono text-faint">{r.file}:{r.line}</p>
                    <p className="font-mono text-fg">{r.snippet}</p>
                  </li>
                ))}
              </ul>
            </Panel>
          )}
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <div role="group" aria-label="Filter plan entries" className="flex flex-wrap gap-1">
          {FILTERS.map((f) => (
            <button
              key={f}
              type="button"
              aria-pressed={filter === f}
              onClick={() => setFilter(f)}
              className={cn('rounded border px-2 py-1 text-xs', filter === f ? 'border-accent/40 bg-accent-soft text-fg' : 'border-line text-muted hover:text-fg')}
            >
              {titleCase(f)}
            </button>
          ))}
        </div>
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Filter by path or package…"
          aria-label="Filter by path or package"
          className="ml-auto h-8 w-64 rounded-md border border-line bg-sunken px-2 text-xs text-fg placeholder:text-faint focus:border-accent focus:outline-none"
        />
      </div>
      <TransformationTable entries={entries} basePackage={p.basePackage} />
    </div>
  );
}

const STRATEGIES: { value: TargetStrategy; title: string; description: string; example: string }[] = [
  {
    value: 'MODULAR_MONOLITH',
    title: 'Modular monolith',
    description:
      'Each module gets a public API (its root package: the types other modules use) and internal sub-packages. Follows Spring Modulith conventions, so the boundaries can be verified.',
    example: 'com.app.order.OrderService · com.app.order.repository.OrderRepository',
  },
  {
    value: 'MODULAR_BY_DOMAIN',
    title: 'Package by module',
    description: 'Groups code by module with layer folders inside, without separating a public API from internals.',
    example: 'com.app.modules.order.service.OrderService',
  },
];

function StrategyPicker({ value, disabled, onChange }: { value: TargetStrategy; disabled: boolean; onChange: (s: TargetStrategy) => void }) {
  return (
    <fieldset className="grid grid-cols-1 gap-3 md:grid-cols-2" disabled={disabled}>
      <legend className="mb-2 text-sm font-medium text-fg">Target architecture</legend>
      {STRATEGIES.map((s) => (
        <label
          key={s.value}
          className={cn(
            'flex cursor-pointer gap-3 rounded-lg border p-3 text-sm',
            value === s.value ? 'border-accent bg-accent-soft' : 'border-line bg-panel hover:bg-panel-hover',
            disabled && 'cursor-not-allowed opacity-60',
          )}
        >
          <input type="radio" name="strategy" className="mt-1" checked={value === s.value} onChange={() => onChange(s.value)} />
          <span className="min-w-0">
            <span className="font-medium text-fg">{s.title}</span>
            {s.value === 'MODULAR_MONOLITH' && <Badge tone="accent" className="ml-2">default</Badge>}
            <span className="mt-1 block text-xs text-muted">{s.description}</span>
            <span className="mt-1 block truncate font-mono text-[11px] text-faint">{s.example}</span>
          </span>
        </label>
      ))}
    </fieldset>
  );
}
