import { ChevronRight } from 'lucide-react';
import { useState } from 'react';
import type { Validation, ValidationLevel } from '../api/types';
import { cn } from '../lib/format';
import { Badge, Panel, StatusBadge } from './ui';

export function ValidationPanel({ validation }: { validation: Validation }) {
  return (
    <div className="space-y-4">
      <ol className="space-y-2" aria-label="Validation levels">
        {validation.levels.map((level, i) => <LevelRow key={level.level} level={level} index={i + 1} />)}
      </ol>
      {validation.build && (
        <Panel
          title="Build output"
          subtitle={`${validation.build.command.join(' ')} · exit ${validation.build.exitCode} · ${(validation.build.durationMillis / 1000).toFixed(1)} s${validation.build.timedOut ? ' · timed out' : ''}`}
        >
          {validation.build.truncated && <p className="mb-2 text-xs text-warn">Output truncated.</p>}
          <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded bg-sunken p-3 font-mono text-[11px] leading-4 text-muted">
            {[validation.build.stdout, validation.build.stderr].filter(Boolean).join('\n') || '(no output)'}
          </pre>
        </Panel>
      )}
    </div>
  );
}

function LevelRow({ level, index }: { level: ValidationLevel; index: number }) {
  const [open, setOpen] = useState(level.status === 'FAIL');
  const expandable = level.issues.length > 0;
  return (
    <li className={cn('rounded-lg border bg-panel', level.status === 'FAIL' ? 'border-fail/40' : 'border-line')}>
      <button
        type="button"
        disabled={!expandable}
        aria-expanded={expandable ? open : undefined}
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center gap-3 px-4 py-3 text-left"
      >
        <span className="w-5 font-mono text-xs text-faint">{index}</span>
        <StatusBadge status={level.status} />
        <span className="font-medium text-fg">{level.label}</span>
        <span className="min-w-0 flex-1 truncate text-sm text-muted">{level.summary}</span>
        <span className="font-mono text-[11px] text-faint">{level.durationMillis} ms</span>
        {expandable && <ChevronRight className={cn('h-4 w-4 text-faint transition-transform', open && 'rotate-90')} aria-hidden />}
      </button>
      {open && expandable && (
        <ul className="divide-y divide-line border-t border-line text-xs">
          {level.issues.map((issue, i) => (
            <li key={i} className="space-y-0.5 px-4 py-2">
              <p className="flex flex-wrap items-center gap-2">
                <Badge tone={issue.severity === 'ERROR' ? 'fail' : 'warn'}>{issue.severity.toLowerCase()}</Badge>
                {issue.file && <span className="font-mono text-faint">{issue.file}{issue.line > 0 ? `:${issue.line}` : ''}</span>}
              </p>
              <p className="text-fg">{issue.message}</p>
              {issue.probableCause && <p className="text-muted">Probable cause: {issue.probableCause}</p>}
            </li>
          ))}
          {level.issueCount > level.issues.length && <li className="px-4 py-2 text-faint">…and {level.issueCount - level.issues.length} more in validation.json</li>}
        </ul>
      )}
    </li>
  );
}
