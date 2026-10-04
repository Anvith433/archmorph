import type { PlanEntry } from '../api/types';
import { cn } from '../lib/format';

/** File list grouped by target module, used to pick a diff. */
export function FileTree({ entries, selected, onSelect }: { entries: PlanEntry[]; selected?: string; onSelect: (id: string) => void }) {
  const groups = new Map<string, PlanEntry[]>();
  for (const entry of entries) {
    const key = entry.module ?? '(unchanged location)';
    groups.set(key, [...(groups.get(key) ?? []), entry]);
  }
  const ordered = [...groups.entries()].sort(([a], [b]) => a.localeCompare(b));
  return (
    <nav aria-label="Files" className="space-y-3">
      {ordered.map(([group, items]) => (
        <div key={group}>
          <p className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-faint">{group}</p>
          <ul>
            {items
              .slice()
              .sort((a, b) => a.className.localeCompare(b.className))
              .map((entry) => (
                <li key={entry.id}>
                  <button
                    type="button"
                    aria-current={selected === entry.id ? 'true' : undefined}
                    onClick={() => onSelect(entry.id)}
                    className={cn(
                      'flex w-full items-center justify-between gap-2 rounded px-2 py-1 text-left font-mono text-xs',
                      selected === entry.id ? 'bg-accent-soft text-fg' : 'text-muted hover:bg-panel-hover hover:text-fg',
                    )}
                  >
                    <span className="truncate">{entry.className}{entry.scope === 'TEST' ? ' (test)' : ''}</span>
                    {entry.safety === 'MANUAL_REVIEW' && <span className="text-fail" title="Manual review">!</span>}
                  </button>
                </li>
              ))}
          </ul>
        </div>
      ))}
    </nav>
  );
}
