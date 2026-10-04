import { diffLines } from 'diff';
import { useMemo } from 'react';
import { cn } from '../lib/format';

export interface DiffRow {
  left?: { number: number; text: string };
  right?: { number: number; text: string };
  kind: 'same' | 'changed' | 'added' | 'removed';
}

function splitLines(value: string): string[] {
  const lines = value.split(/\r?\n/);
  if (lines.length > 0 && lines[lines.length - 1] === '') lines.pop();
  return lines;
}

/** Aligns a line diff into side-by-side rows; removed/added runs are paired line by line. */
export function sideBySide(before: string, after: string): DiffRow[] {
  const rows: DiffRow[] = [];
  let l = 1;
  let r = 1;
  const parts = diffLines(before.replace(/\r\n/g, '\n'), after.replace(/\r\n/g, '\n'));
  for (let i = 0; i < parts.length; i++) {
    const part = parts[i];
    const lines = splitLines(part.value);
    if (!part.added && !part.removed) {
      for (const text of lines) rows.push({ kind: 'same', left: { number: l++, text }, right: { number: r++, text } });
    } else if (part.removed && parts[i + 1]?.added) {
      const added = splitLines(parts[i + 1].value);
      const n = Math.max(lines.length, added.length);
      for (let k = 0; k < n; k++) {
        rows.push({
          kind: 'changed',
          left: k < lines.length ? { number: l++, text: lines[k] } : undefined,
          right: k < added.length ? { number: r++, text: added[k] } : undefined,
        });
      }
      i++;
    } else if (part.removed) {
      for (const text of lines) rows.push({ kind: 'removed', left: { number: l++, text } });
    } else {
      for (const text of lines) rows.push({ kind: 'added', right: { number: r++, text } });
    }
  }
  return rows;
}

/** Collapses long unchanged stretches, keeping `context` lines around each change. */
export function collapse(rows: DiffRow[], context = 4): (DiffRow | { gap: number })[] {
  const keep = new Array<boolean>(rows.length).fill(false);
  rows.forEach((row, i) => {
    if (row.kind !== 'same') {
      for (let k = Math.max(0, i - context); k <= Math.min(rows.length - 1, i + context); k++) keep[k] = true;
    }
  });
  const out: (DiffRow | { gap: number })[] = [];
  let gap = 0;
  rows.forEach((row, i) => {
    if (keep[i]) {
      if (gap) out.push({ gap });
      gap = 0;
      out.push(row);
    } else {
      gap++;
    }
  });
  if (gap) out.push({ gap });
  return out;
}

/**
 * Side-by-side Java diff. Source is rendered strictly as text nodes — uploaded code is never
 * interpreted as HTML.
 */
export function CodeDiff({ before, after, beforeLabel, afterLabel, expanded = false }: {
  before: string;
  after: string;
  beforeLabel: string;
  afterLabel: string;
  expanded?: boolean;
}) {
  const rows = useMemo(() => sideBySide(before, after), [before, after]);
  const display = useMemo(() => (expanded ? rows : collapse(rows)), [rows, expanded]);

  return (
    <div className="relative overflow-x-auto rounded-lg border border-line bg-sunken font-mono text-[12px] leading-5">
      <table className="w-full min-w-[900px] table-fixed border-collapse">
        <caption className="sr-only">Changes from {beforeLabel} to {afterLabel}</caption>
        <colgroup>
          <col className="w-12" />
          <col />
          <col className="w-12" />
          <col />
        </colgroup>
        <thead className="border-b border-line bg-elevated text-left text-[11px] text-muted">
          <tr>
            <th scope="col" colSpan={2} className="truncate px-3 py-1.5 font-medium">{beforeLabel}</th>
            <th scope="col" colSpan={2} className="truncate border-l border-line px-3 py-1.5 font-medium">{afterLabel}</th>
          </tr>
        </thead>
        <tbody>
          {display.map((row, i) =>
            'gap' in row ? (
              <tr key={`gap-${i}`} className="bg-panel text-[11px] text-faint">
                <td colSpan={4} className="px-3 py-0.5 text-center">⋯ {row.gap} unchanged line{row.gap === 1 ? '' : 's'}</td>
              </tr>
            ) : (
              <tr key={i}>
                <td className="select-none px-2 text-right align-top text-faint">{row.left?.number}</td>
                <td className={cn('whitespace-pre px-2 align-top text-fg', row.left && row.kind !== 'same' && 'bg-diff-del')}>
                  {row.left && row.kind !== 'same' && <span className="sr-only">removed: </span>}
                  {row.left?.text}
                </td>
                <td className="select-none border-l border-line px-2 text-right align-top text-faint">{row.right?.number}</td>
                <td className={cn('whitespace-pre px-2 align-top text-fg', row.right && row.kind !== 'same' && 'bg-diff-add')}>
                  {row.right && row.kind !== 'same' && <span className="sr-only">added: </span>}
                  {row.right?.text}
                </td>
              </tr>
            ),
          )}
        </tbody>
      </table>
    </div>
  );
}
