import { useMemo } from 'react';
import type { Plan } from '../api/types';
import { shortPackage, titleCase } from '../lib/format';

/** Where each class lands in the chosen layout and in the alternative, for classes whose package differs. */
export function LayoutComparison({ current, other }: { current: Plan; other: Plan }) {
  const rows = useMemo(() => {
    const otherByEntry = new Map(other.entries.map((e) => [e.sourcePath, e]));
    return current.entries
      .filter((e) => e.scope === 'MAIN')
      .map((e) => ({ entry: e, alternative: otherByEntry.get(e.sourcePath) }))
      .filter((r) => r.alternative && r.alternative.targetPackage !== r.entry.targetPackage)
      .sort((a, b) => a.entry.className.localeCompare(b.entry.className));
  }, [current, other]);

  return (
    <div className="relative overflow-x-auto rounded-lg border border-line">
      <table className="w-full min-w-[760px] text-left text-sm">
        <caption className="sr-only">Target package per class in both layouts</caption>
        <thead className="border-b border-line bg-elevated text-xs text-muted">
          <tr>
            <th scope="col" className="px-3 py-2 font-medium">Class</th>
            <th scope="col" className="px-3 py-2 font-medium">{titleCase(current.strategy)} (selected)</th>
            <th scope="col" className="px-3 py-2 font-medium">{titleCase(other.strategy)}</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rows.map(({ entry, alternative }) => (
            <tr key={entry.id} className="bg-panel">
              <td className="px-3 py-1.5 font-mono text-fg">{entry.className}</td>
              <td className="px-3 py-1.5 font-mono text-xs text-fg">{shortPackage(entry.targetPackage, current.basePackage)}</td>
              <td className="px-3 py-1.5 font-mono text-xs text-muted">{alternative ? shortPackage(alternative.targetPackage, other.basePackage) : '—'}</td>
            </tr>
          ))}
          {rows.length === 0 && (
            <tr><td colSpan={3} className="px-3 py-3 text-xs text-muted">Both layouts place every class in the same package.</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
