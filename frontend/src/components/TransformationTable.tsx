import { ArrowDown, ArrowUp, ChevronRight } from 'lucide-react';
import { Fragment, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import type { PlanEntry } from '../api/types';
import { cn, shortPackage } from '../lib/format';
import { Badge, StatusBadge } from './ui';

type SortKey = 'className' | 'sourcePackage' | 'targetPackage' | 'module' | 'confidence' | 'risk';
const RISK_ORDER = { LOW: 0, MEDIUM: 1, HIGH: 2 } as const;

function compare(a: PlanEntry, b: PlanEntry, key: SortKey): number {
  switch (key) {
    case 'confidence':
      return a.confidence - b.confidence;
    case 'risk':
      return RISK_ORDER[a.risk] - RISK_ORDER[b.risk];
    case 'module':
      return (a.module ?? '').localeCompare(b.module ?? '');
    default:
      return a[key].localeCompare(b[key]);
  }
}

/** Primary action shown in the table; the full action list is in the expanded row. */
export function primaryAction(entry: PlanEntry): string {
  for (const action of ['EXCLUDE', 'MANUAL_REVIEW', 'MOVE', 'KEEP']) {
    if (entry.actions.includes(action)) return action;
  }
  return entry.actions[0] ?? 'KEEP';
}

export function TransformationTable({ entries, basePackage }: { entries: PlanEntry[]; basePackage: string }) {
  const [sort, setSort] = useState<{ key: SortKey; asc: boolean }>({ key: 'className', asc: true });
  const [expanded, setExpanded] = useState<string | null>(null);
  const rows = useMemo(() => {
    const sorted = [...entries].sort((a, b) => compare(a, b, sort.key) || a.sourcePath.localeCompare(b.sourcePath));
    return sort.asc ? sorted : sorted.reverse();
  }, [entries, sort]);

  const header = (key: SortKey, label: string, className?: string) => (
    <th scope="col" className={cn('px-3 py-2 font-medium', className)} aria-sort={sort.key === key ? (sort.asc ? 'ascending' : 'descending') : 'none'}>
      <button type="button" className="inline-flex items-center gap-1 hover:text-fg" onClick={() => setSort((s) => ({ key, asc: s.key === key ? !s.asc : true }))}>
        {label}
        {sort.key === key && (sort.asc ? <ArrowUp className="h-3 w-3" aria-hidden /> : <ArrowDown className="h-3 w-3" aria-hidden />)}
      </button>
    </th>
  );

  return (
    <div className="relative overflow-x-auto rounded-lg border border-line">
      <table className="w-full min-w-[960px] text-left text-sm">
        <caption className="sr-only">Transformation plan, one row per source file</caption>
        <thead className="border-b border-line bg-elevated text-xs text-muted">
          <tr>
            <th scope="col" className="w-8 px-2"><span className="sr-only">Details</span></th>
            {header('className', 'Class')}
            {header('sourcePackage', 'Current package')}
            {header('targetPackage', 'Target package')}
            {header('module', 'Module')}
            <th scope="col" className="px-3 py-2 font-medium">Action</th>
            {header('confidence', 'Confidence', 'text-right')}
            {header('risk', 'Risk')}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rows.map((entry) => {
            const open = expanded === entry.id;
            const action = primaryAction(entry);
            return (
              <Fragment key={entry.id}>
                <tr className={cn('bg-panel hover:bg-panel-hover', entry.safety === 'MANUAL_REVIEW' && 'bg-fail-soft/40')}>
                  <td className="px-2">
                    <button
                      type="button"
                      aria-expanded={open}
                      aria-label={`${open ? 'Hide' : 'Show'} details for ${entry.className}`}
                      onClick={() => setExpanded(open ? null : entry.id)}
                      className="rounded p-1 text-faint hover:text-fg"
                    >
                      <ChevronRight className={cn('h-4 w-4 transition-transform', open && 'rotate-90')} aria-hidden />
                    </button>
                  </td>
                  <td className="px-3 py-2">
                    <span className="font-mono text-fg">{entry.className}</span>
                    {entry.scope === 'TEST' && <Badge className="ml-2">test</Badge>}
                  </td>
                  <td className="px-3 py-2 font-mono text-xs text-muted">{shortPackage(entry.sourcePackage, basePackage)}</td>
                  <td className="px-3 py-2 font-mono text-xs text-fg">{shortPackage(entry.targetPackage, basePackage)}</td>
                  <td className="px-3 py-2 text-xs">{entry.module ?? <span className="text-faint">—</span>}</td>
                  <td className="px-3 py-2">
                    <Badge tone={action === 'MOVE' ? 'accent' : action === 'MANUAL_REVIEW' ? 'fail' : action === 'EXCLUDE' ? 'warn' : 'neutral'}>{action.toLowerCase().replace('_', ' ')}</Badge>
                  </td>
                  <td className="px-3 py-2 text-right font-mono text-xs tabular-nums text-muted">{Math.round(entry.confidence * 100)}%</td>
                  <td className="px-3 py-2"><StatusBadge status={entry.risk} /></td>
                </tr>
                {open && (
                  <tr className="bg-sunken">
                    <td />
                    <td colSpan={7} className="px-3 py-3">
                      <div className="grid grid-cols-1 gap-4 text-xs md:grid-cols-2">
                        <div className="space-y-2">
                          <p><span className="text-muted">Safety:</span> <StatusBadge status={entry.safety} /></p>
                          <p className="break-all font-mono text-muted">{entry.sourcePath}<br />→ <span className="text-fg">{entry.targetPath}</span></p>
                          <p><span className="text-muted">Actions:</span> <span className="font-mono">{entry.actions.join(', ')}</span></p>
                          {entry.classes.length > 1 && (
                            <div>
                              <p className="text-muted">Types in this file</p>
                              <ul className="font-mono">{entry.classes.map((c) => <li key={c.source}>{c.source} → {c.target}{c.nested ? ' (nested)' : ''}</li>)}</ul>
                            </div>
                          )}
                          <Link to={`../diff?entry=${entry.id}`} className="inline-block font-medium text-accent hover:underline">Open diff →</Link>
                        </div>
                        <div className="space-y-2">
                          {entry.reasons.length > 0 && (
                            <div>
                              <p className="text-muted">Why</p>
                              <ul className="list-disc space-y-0.5 pl-4 text-fg">{entry.reasons.map((r) => <li key={r}>{r}</li>)}</ul>
                            </div>
                          )}
                          {entry.rewrites.length > 0 && (
                            <div>
                              <p className="text-muted">Rewrites ({entry.rewrites.length})</p>
                              <ul className="max-h-32 overflow-y-auto font-mono text-muted">{entry.rewrites.map((r) => <li key={r}>{r}</li>)}</ul>
                            </div>
                          )}
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
