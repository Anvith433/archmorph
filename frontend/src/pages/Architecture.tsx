import { ArrowRight, Folder, FolderOpen } from 'lucide-react';
import { useState } from 'react';
import { api } from '../api/client';
import type { ArchitectureView } from '../api/types';
import { ErrorPanel } from '../components/ErrorPanel';
import { Badge, Meter, Panel, Skeleton } from '../components/ui';
import { useProject } from '../hooks/ProjectContext';
import { useResource } from '../hooks/useResource';
import { titleCase } from '../lib/format';

export function Architecture() {
  const { projectId, version } = useProject();
  const view = useResource(() => api.architecture(projectId), `${projectId}:${version}`);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-fg">Architecture</h1>
        <p className="text-sm text-muted">The layout as it is today next to the proposed modular-by-domain layout. Nothing is moved until you transform.</p>
      </div>
      {view.error !== undefined && <ErrorPanel error={view.error} onRetry={view.reload} />}
      {!view.data && view.error === undefined && <div className="grid grid-cols-1 gap-6 lg:grid-cols-2"><Skeleton className="h-96" /><Skeleton className="h-96" /></div>}
      {view.data && <ArchitectureDiagram view={view.data} />}
    </div>
  );
}

export function ArchitectureDiagram({ view }: { view: ArchitectureView }) {
  const packages = Object.entries(view.current.packages).sort(([a], [b]) => a.localeCompare(b));
  return (
    <>
      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[1fr_auto_1fr]">
        <Panel title="Current" subtitle={`Package style: ${titleCase(view.current.packageStyle)}`}>
          <div className="space-y-4">
            <div className="space-y-2">
              {view.current.layers.map((layer) => (
                <details key={layer.componentType} className="group rounded-md border border-line bg-sunken">
                  <summary className="flex cursor-pointer list-none items-center justify-between px-3 py-2 text-sm">
                    <span className="font-medium text-fg">{layer.name}</span>
                    <Badge>{layer.count}</Badge>
                  </summary>
                  <ul className="border-t border-line px-3 py-2 font-mono text-xs text-muted">
                    {layer.classes.map((c) => <li key={c} className="truncate">{c}</li>)}
                  </ul>
                </details>
              ))}
            </div>
            <div>
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-faint">Packages</h3>
              <ul className="space-y-1 font-mono text-xs">
                {packages.map(([pkg, count]) => (
                  <li key={pkg} className="flex justify-between gap-3 text-muted">
                    <span className="truncate">{pkg || '(default package)'}</span>
                    <span className="tabular-nums text-faint">{count}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </Panel>

        <div className="hidden items-center self-center lg:flex" aria-hidden>
          <ArrowRight className="h-6 w-6 text-faint" />
        </div>

        <Panel title="Proposed" subtitle={`${titleCase(view.proposed.strategy)} · base package ${view.proposed.basePackage || '(default)'}`}>
          <div className="space-y-2">
            {view.proposed.modules.map((module) => (
              <ModuleTree key={module.name} name={module.name} folders={module.folders} confidence={module.confidence} count={module.classCount} />
            ))}
            {Object.keys(view.proposed.shared).length > 0 && (
              <ModuleTree name="shared" folders={view.proposed.shared} count={Object.values(view.proposed.shared).flat().length} tone="info" />
            )}
            {view.proposed.application.length > 0 && (
              <ModuleTree name="(application root)" folders={{ '': view.proposed.application }} count={view.proposed.application.length} />
            )}
          </div>
        </Panel>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Panel title="Target layout" subtitle="Folder template applied to every module.">
          <pre className="overflow-x-auto font-mono text-xs leading-5 text-muted">{view.proposed.layout.join('\n')}</pre>
        </Panel>
        {view.notes.length > 0 && (
          <Panel title="Notes">
            <ul className="list-disc space-y-1 pl-5 text-sm text-muted">
              {view.notes.map((n) => <li key={n}>{n}</li>)}
            </ul>
          </Panel>
        )}
      </div>
    </>
  );
}

function ModuleTree({ name, folders, confidence, count, tone = 'accent' }: {
  name: string;
  folders: Record<string, string[]>;
  confidence?: number;
  count: number;
  tone?: 'accent' | 'info';
}) {
  const [open, setOpen] = useState(false);
  const entries = Object.entries(folders).sort(([a], [b]) => a.localeCompare(b));
  return (
    <div className="rounded-md border border-line bg-sunken">
      <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open} className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm">
        {open ? <FolderOpen className="h-4 w-4 text-accent" aria-hidden /> : <Folder className="h-4 w-4 text-accent" aria-hidden />}
        <span className="font-medium text-fg">{name}</span>
        <Badge tone={tone}>{count} classes</Badge>
        {confidence !== undefined && <div className="ml-auto w-32"><Meter value={confidence} label={`${name} confidence`} /></div>}
      </button>
      {open && (
        <ul className="space-y-2 border-t border-line px-3 py-2 font-mono text-xs">
          {entries.map(([folder, classes]) => (
            <li key={folder}>
              {folder && <p className="text-faint">{folder}/</p>}
              <ul className={folder ? 'pl-4 text-muted' : 'text-muted'}>
                {classes.map((c) => <li key={c}>{c}</li>)}
              </ul>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
