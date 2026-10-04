import { GitMerge } from 'lucide-react';
import type { BoundarySuggestion, ModuleEdit } from '../api/types';
import { Badge, Button, Panel, type Tone } from './ui';

const KIND: Record<BoundarySuggestion['kind'], { label: string; tone: Tone }> = {
  MOVE_CLASS: { label: 'move class', tone: 'ok' },
  SPLIT_FACADE: { label: 'split facade', tone: 'warn' },
  UNIDIRECTIONAL_RELATIONSHIP: { label: 'entity relationship', tone: 'info' },
  INVERT_DEPENDENCY: { label: 'invert dependency', tone: 'accent' },
};

/**
 * Module cycles and concrete ways to break them. Only "move class" suggestions can be applied here; the others
 * describe a code change for the team to make.
 */
export function CyclePanel({ cycles, suggestions, editable, onApply }: {
  cycles: string[][];
  suggestions: BoundarySuggestion[];
  editable: boolean;
  onApply: (edit: ModuleEdit) => void;
}) {
  if (cycles.length === 0) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-ok/30 bg-ok-soft px-4 py-3 text-sm text-fg">
        <GitMerge className="h-4 w-4 text-ok" aria-hidden /> No cycles between modules: every module dependency points one way.
      </div>
    );
  }
  return (
    <Panel
      title="Module cycles"
      subtitle={`${cycles.map((c) => c.join(' ↔ ')).join('; ')} depend on each other. Moving packages cannot remove a cycle that exists in the code; these suggestions together make the modules acyclic.`}
    >
      <ol className="space-y-3">
        {suggestions.map((s) => (
          <li key={s.id} className="rounded-md border border-line bg-sunken p-3 text-sm">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone={KIND[s.kind].tone}>{KIND[s.kind].label}</Badge>
              <span className="font-medium text-fg">{s.title}</span>
              <span className="text-xs text-faint">{s.dependencyCount} dependencies</span>
              {s.edit && (
                <Button size="sm" variant="primary" className="ml-auto" disabled={!editable} onClick={() => s.edit && onApply(s.edit)}>
                  Apply
                </Button>
              )}
            </div>
            <p className="mt-1 text-xs text-muted">{s.rationale}</p>
            <ol className="mt-2 list-decimal space-y-1 pl-5 text-xs text-fg">
              {s.steps.map((step) => <li key={step}>{step}</li>)}
            </ol>
            <details className="mt-2 text-xs text-muted">
              <summary className="cursor-pointer text-faint">Dependencies involved</summary>
              <ul className="mt-1 space-y-0.5 font-mono text-[11px]">{s.evidence.map((e) => <li key={e}>{e}</li>)}</ul>
            </details>
          </li>
        ))}
      </ol>
    </Panel>
  );
}
