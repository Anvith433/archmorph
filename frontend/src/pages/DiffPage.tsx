import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import { CodeDiff } from '../components/CodeDiff';
import { ErrorPanel } from '../components/ErrorPanel';
import { FileTree } from '../components/FileTree';
import { Badge, EmptyState, Panel, Skeleton, StatusBadge } from '../components/ui';
import { useProject } from '../hooks/ProjectContext';
import { useResource } from '../hooks/useResource';

export function DiffPage() {
  const { projectId, version } = useProject();
  const plan = useResource(() => api.plan(projectId), `${projectId}:${version}`);
  const [params, setParams] = useSearchParams();
  const [changedOnly, setChangedOnly] = useState(true);
  const [expanded, setExpanded] = useState(false);

  const entries = useMemo(
    () => (plan.data?.entries ?? []).filter((e) => !changedOnly || e.sourcePath !== e.targetPath || e.rewrites.length > 0),
    [plan.data, changedOnly],
  );
  const selected = params.get('entry') ?? entries[0]?.id;
  const entry = plan.data?.entries.find((e) => e.id === selected);
  const diff = useResource(() => api.diff(projectId, selected ?? ''), selected ? `${projectId}:${version}:${selected}` : null);

  if (plan.error !== undefined) return <ErrorPanel error={plan.error} onRetry={plan.reload} />;
  if (!plan.data) return <Skeleton className="h-[480px]" />;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-fg">Diff</h1>
        <p className="text-sm text-muted">What the AST rewriter would change in each file: package declaration, imports and qualified references. Formatting and comments are preserved.</p>
      </div>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[260px_1fr]">
        <Panel className="h-fit lg:sticky lg:top-16" padded={false}>
          <div className="border-b border-line px-3 py-2">
            <label className="flex items-center gap-2 text-xs text-muted">
              <input type="checkbox" checked={changedOnly} onChange={(e) => setChangedOnly(e.target.checked)} />
              Changed files only
            </label>
          </div>
          <div className="max-h-[70vh] overflow-y-auto p-2">
            <FileTree entries={entries} selected={selected} onSelect={(id) => setParams({ entry: id }, { replace: true })} />
          </div>
        </Panel>
        <div className="min-w-0 space-y-3">
          {!selected && <EmptyState title="No file selected" />}
          {entry && (
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="font-mono font-medium text-fg">{entry.className}</span>
              <StatusBadge status={entry.safety} />
              {diff.data && <Badge>{diff.data.importChanges.length} import changes</Badge>}
              {diff.data && <Badge>{diff.data.qualifiedRewrites} qualified references</Badge>}
              <label className="ml-auto flex items-center gap-2 text-xs text-muted">
                <input type="checkbox" checked={expanded} onChange={(e) => setExpanded(e.target.checked)} />
                Show unchanged lines
              </label>
            </div>
          )}
          {entry && entry.reasons.length > 0 && entry.safety !== 'SAFE' && (
            <ul className="list-disc rounded-lg border border-warn/30 bg-warn-soft py-2 pl-8 pr-4 text-xs text-fg">{entry.reasons.map((r) => <li key={r}>{r}</li>)}</ul>
          )}
          {diff.error !== undefined && <ErrorPanel error={diff.error} onRetry={diff.reload} />}
          {selected && !diff.data && diff.error === undefined && <Skeleton className="h-96" />}
          {diff.data && !diff.data.changed && <EmptyState title="This file is not modified">It keeps its location and content.</EmptyState>}
          {diff.data && diff.data.changed && (
            <>
              {diff.data.truncated && <p className="text-xs text-warn">This file is large; the diff is truncated.</p>}
              <CodeDiff before={diff.data.before} after={diff.data.after} beforeLabel={diff.data.sourcePath} afterLabel={diff.data.targetPath} expanded={expanded} />
            </>
          )}
        </div>
      </div>
    </div>
  );
}
