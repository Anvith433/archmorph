import { RotateCcw, Undo2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { ModuleEdit, Modules as ModulesData } from '../api/types';
import { ErrorPanel } from '../components/ErrorPanel';
import { CyclePanel } from '../components/CyclePanel';
import { ModuleCard } from '../components/ModuleCard';
import { Badge, Button, Panel, Skeleton } from '../components/ui';
import { useProject } from '../hooks/ProjectContext';
import { useResource } from '../hooks/useResource';
import { cn, titleCase } from '../lib/format';

type View = 'final' | 'suggestion';

export function describeEdit(edit: ModuleEdit): string {
  const short = (fqn?: string | null) => (fqn ? fqn.substring(fqn.lastIndexOf('.') + 1) : '?');
  switch (edit.type) {
    case 'RENAME_MODULE':
      return `Rename ${edit.module} → ${edit.newName}`;
    case 'MERGE_MODULES':
      return `Merge ${(edit.sources ?? []).join(', ')} into ${edit.target}`;
    case 'SPLIT_MODULE':
      return `Split ${edit.classes?.length ?? 0} classes out of ${edit.module} into ${edit.newName}`;
    case 'MOVE_CLASS':
      return `Move ${short(edit.className)} → ${edit.target}`;
    case 'MOVE_TO_SHARED':
      return `Move ${short(edit.className)} → shared`;
    default:
      return `${titleCase(edit.type.replace('_CLASS', ''))} ${short(edit.className)}`;
  }
}

/** Module review and editor. Every decision is validated by the server, which replays the full list on top of the suggestion. */
export function Modules() {
  const { projectId, version, project, refresh } = useProject();
  const remote = useResource(() => api.modules(projectId), `${projectId}:${version}`);
  const [data, setData] = useState<ModulesData>();
  const [view, setView] = useState<View>('final');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>();
  const editable = Boolean(project?.capabilities.canEditModules);

  useEffect(() => {
    if (remote.data) setData(remote.data);
  }, [remote.data]);

  const submit = async (decisions: ModuleEdit[]) => {
    setSaving(true);
    setError(undefined);
    try {
      setData(await api.updateModules(projectId, decisions));
      refresh();
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
    }
  };

  if (remote.error !== undefined && !data) {
    return <ErrorPanel error={remote.error} onRetry={remote.reload} />;
  }
  if (!data) {
    return <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 2xl:grid-cols-3">{Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-64" />)}</div>;
  }

  const modules = view === 'final' ? data.finalModules : data.suggestion;
  const names = data.finalModules.map((m) => m.name);
  const order = (category: string) => (category === 'BUSINESS_MODULE' ? 0 : category === 'SHARED' ? 2 : 1);
  const sorted = [...modules].sort((a, b) => order(a.category) - order(b.category) || a.name.localeCompare(b.name));

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-fg">Modules</h1>
          <p className="max-w-3xl text-sm text-muted">{data.note}</p>
        </div>
        <div className="flex items-center gap-2">
          <div role="tablist" aria-label="Module view" className="flex rounded-md border border-line p-0.5">
            {(['final', 'suggestion'] as View[]).map((v) => (
              <button
                key={v}
                role="tab"
                type="button"
                aria-selected={view === v}
                onClick={() => setView(v)}
                className={cn('rounded px-3 py-1 text-xs font-medium', view === v ? 'bg-accent-soft text-fg' : 'text-muted hover:text-fg')}
              >
                {v === 'final' ? 'Final plan' : 'ArchMorph suggestion'}
              </button>
            ))}
          </div>
          <Link to="../plan" className="text-sm font-medium text-accent hover:underline">View plan →</Link>
        </div>
      </div>

      {error !== undefined && <ErrorPanel error={error} title="That change was not applied" />}
      {data.warnings.length > 0 && (
        <ul className="space-y-1 rounded-lg border border-warn/30 bg-warn-soft px-4 py-3 text-sm">
          {data.warnings.map((w) => <li key={w} className="text-fg">{w}</li>)}
        </ul>
      )}

      {view === 'final' && (
        <CyclePanel
          cycles={data.cycles ?? []}
          suggestions={data.boundarySuggestions ?? []}
          editable={editable && !saving}
          onApply={(edit) => void submit([...data.decisions, edit])}
        />
      )}

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1fr_300px]">
        <div className="grid grid-cols-1 content-start gap-4 lg:grid-cols-2 2xl:grid-cols-3">
          {sorted.map((m) => (
            <ModuleCard
              key={`${view}-${m.name}`}
              module={m}
              moduleNames={names}
              editable={editable && view === 'final' && !saving}
              onEdit={(edit) => void submit([...data.decisions, edit])}
            />
          ))}
        </div>
        <Panel
          title="Your decisions"
          subtitle="Replayed in order on top of the suggestion."
          className="h-fit xl:sticky xl:top-16"
          actions={
            <>
              <Button size="sm" variant="ghost" disabled={!editable || data.decisions.length === 0} busy={saving} onClick={() => void submit(data.decisions.slice(0, -1))}>
                <Undo2 className="h-3.5 w-3.5" aria-hidden /> Undo
              </Button>
              <Button size="sm" variant="ghost" disabled={!editable || data.decisions.length === 0 || saving} onClick={() => void submit([])}>
                <RotateCcw className="h-3.5 w-3.5" aria-hidden /> Reset
              </Button>
            </>
          }
        >
          {data.decisions.length === 0 ? (
            <p className="text-sm text-muted">
              None yet. Drag a class onto another module, or use its “Move to” menu. Locked classes stay put; excluded classes keep their current package.
            </p>
          ) : (
            <ol className="space-y-1.5 text-sm">
              {data.decisions.map((d, i) => (
                <li key={i} className="flex gap-2">
                  <span className="font-mono text-xs text-faint">{i + 1}.</span>
                  <span className="text-fg">{describeEdit(d)}</span>
                </li>
              ))}
            </ol>
          )}
          {!editable && <p className="mt-3 text-xs text-faint">Editing is disabled while a job is running or after the project failed.</p>}
          <div className="mt-4 flex flex-wrap gap-1.5 text-[11px]">
            <Badge tone="accent">Business module</Badge>
            <Badge tone="info">Shared</Badge>
            <Badge tone="warn">Security</Badge>
          </div>
        </Panel>
      </div>
    </div>
  );
}
