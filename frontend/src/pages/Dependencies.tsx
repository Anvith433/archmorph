import { Search, X } from 'lucide-react';
import { useMemo, useState } from 'react';
import { api } from '../api/client';
import type { Graph, GraphEdge, GraphNode } from '../api/types';
import { DependencyGraph } from '../components/DependencyGraph';
import { ErrorPanel } from '../components/ErrorPanel';
import { Badge, EmptyState, Meter, Panel, Skeleton } from '../components/ui';
import { useProject } from '../hooks/ProjectContext';
import { useResource } from '../hooks/useResource';
import { cn, titleCase } from '../lib/format';

const TYPES = ['CONTROLLER', 'SERVICE', 'REPOSITORY', 'ENTITY', 'DTO', 'CONFIGURATION', 'SECURITY', 'OTHER'] as const;
type TypeFilter = (typeof TYPES)[number];
const MAIN_TYPES = new Set<string>(TYPES.filter((t) => t !== 'OTHER'));

/** Above this many nodes the layout is slow and unreadable; the user is asked to filter first. */
export const MAX_RENDERED_NODES = 350;

export interface GraphFilters {
  types: Set<TypeFilter>;
  crossModuleOnly: boolean;
  cyclesOnly: boolean;
  violationsOnly: boolean;
  query: string;
}

export function filterGraph(graph: Graph, filters: GraphFilters): { nodes: GraphNode[]; edges: GraphEdge[] } {
  const query = filters.query.trim().toLowerCase();
  const typeOf = (n: GraphNode): TypeFilter => (MAIN_TYPES.has(n.componentType) ? (n.componentType as TypeFilter) : 'OTHER');
  let nodes = graph.nodes.filter((n) => filters.types.has(typeOf(n)));
  const ids = new Set(nodes.map((n) => n.id));
  let edges = graph.edges.filter((e) => ids.has(e.source) && ids.has(e.target));
  const edgeFilterActive = filters.crossModuleOnly || filters.cyclesOnly || filters.violationsOnly;
  if (edgeFilterActive) {
    edges = edges.filter((e) =>
      (!filters.crossModuleOnly || e.crossModule) && (!filters.cyclesOnly || e.inCycle) && (!filters.violationsOnly || e.violation));
    const touched = new Set(edges.flatMap((e) => [e.source, e.target]));
    nodes = nodes.filter((n) => touched.has(n.id));
  }
  if (query) {
    const matches = new Set(nodes.filter((n) => n.id.toLowerCase().includes(query)).map((n) => n.id));
    const keep = new Set(matches);
    for (const e of edges) {
      if (matches.has(e.source)) keep.add(e.target);
      if (matches.has(e.target)) keep.add(e.source);
    }
    nodes = nodes.filter((n) => keep.has(n.id));
    edges = edges.filter((e) => keep.has(e.source) && keep.has(e.target));
  }
  return { nodes, edges };
}

export function Dependencies() {
  const { projectId, version } = useProject();
  const graph = useResource(() => api.dependencies(projectId), `${projectId}:${version}`);
  const [types, setTypes] = useState<Set<TypeFilter>>(() => new Set(TYPES));
  const [crossModuleOnly, setCrossModuleOnly] = useState(false);
  const [cyclesOnly, setCyclesOnly] = useState(false);
  const [violationsOnly, setViolationsOnly] = useState(false);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState<string>();

  const filtered = useMemo(
    () => (graph.data ? filterGraph(graph.data, { types, crossModuleOnly, cyclesOnly, violationsOnly, query }) : { nodes: [], edges: [] }),
    [graph.data, types, crossModuleOnly, cyclesOnly, violationsOnly, query],
  );
  const selectedNode = graph.data?.nodes.find((n) => n.id === selected);

  const toggleType = (type: TypeFilter) =>
    setTypes((current) => {
      const next = new Set(current);
      if (next.has(type)) next.delete(type);
      else next.add(type);
      return next;
    });

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold text-fg">Dependencies</h1>
        <p className="text-sm text-muted">
          Semantic class-to-class dependencies from the AST. Dashed edges cross module boundaries, red edges are part of a cycle, amber edges violate a layer rule.
        </p>
      </div>

      {graph.error !== undefined && <ErrorPanel error={graph.error} onRetry={graph.reload} />}
      {!graph.data && graph.error === undefined && <Skeleton className="h-[560px]" />}

      {graph.data && (
        <>
          <fieldset className="flex flex-wrap items-center gap-2 rounded-lg border border-line bg-panel px-3 py-2">
            <legend className="sr-only">Graph filters</legend>
            {TYPES.map((type) => (
              <label key={type} className={cn('flex cursor-pointer items-center gap-1.5 rounded border px-2 py-1 text-xs', types.has(type) ? 'border-accent/40 bg-accent-soft text-fg' : 'border-line text-muted')}>
                <input type="checkbox" className="accent-[var(--accent)]" checked={types.has(type)} onChange={() => toggleType(type)} />
                {titleCase(type)}
              </label>
            ))}
            <span className="mx-1 h-5 w-px bg-line" aria-hidden />
            <Toggle label="Cross-module" checked={crossModuleOnly} onChange={setCrossModuleOnly} />
            <Toggle label="Cycles" checked={cyclesOnly} onChange={setCyclesOnly} />
            <Toggle label="Violations" checked={violationsOnly} onChange={setViolationsOnly} />
            <div className="relative ml-auto">
              <Search className="pointer-events-none absolute left-2 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-faint" aria-hidden />
              <input
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Find class…"
                aria-label="Find class"
                className="h-8 w-56 rounded-md border border-line bg-sunken pl-7 pr-2 text-xs text-fg placeholder:text-faint focus:border-accent focus:outline-none"
              />
            </div>
          </fieldset>

          <p className="text-xs text-faint">
            Showing {filtered.nodes.length} of {graph.data.totalNodes} classes and {filtered.edges.length} of {graph.data.totalEdges} dependencies
            {graph.data.truncated ? ' (the server truncated this graph; use the JSON report for everything)' : ''}.
          </p>

          <div className="grid grid-cols-1 gap-4 xl:grid-cols-[1fr_320px]">
            <div className="h-[600px] overflow-hidden rounded-lg border border-line bg-sunken">
              {filtered.nodes.length === 0 ? (
                <div className="p-6"><EmptyState title="Nothing matches these filters" /></div>
              ) : filtered.nodes.length > MAX_RENDERED_NODES ? (
                <div className="p-6">
                  <EmptyState title={`${filtered.nodes.length} classes are too many to draw`}>
                    Narrow the view with the type filters or the search box (limit {MAX_RENDERED_NODES}).
                  </EmptyState>
                </div>
              ) : (
                <DependencyGraph nodes={filtered.nodes} edges={filtered.edges} selectedId={selected} onSelect={setSelected} />
              )}
            </div>
            <NodeDetails node={selectedNode} graph={graph.data} onClose={() => setSelected(undefined)} onSelect={setSelected} />
          </div>
        </>
      )}
    </div>
  );
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  return (
    <button
      type="button"
      aria-pressed={checked}
      onClick={() => onChange(!checked)}
      className={cn('rounded border px-2 py-1 text-xs', checked ? 'border-accent/40 bg-accent-soft text-fg' : 'border-line text-muted hover:text-fg')}
    >
      {label} only
    </button>
  );
}

function NodeDetails({ node, graph, onClose, onSelect }: { node?: GraphNode; graph: Graph; onClose: () => void; onSelect: (id: string) => void }) {
  if (!node) {
    return (
      <Panel title="Details">
        <p className="text-sm text-muted">Select a class to see its dependencies, coupling and classification confidence.</p>
      </Panel>
    );
  }
  const outgoing = graph.edges.filter((e) => e.source === node.id);
  const incoming = graph.edges.filter((e) => e.target === node.id);
  const name = (id: string) => graph.nodes.find((n) => n.id === id)?.className ?? id;
  return (
    <Panel
      title={<span className="font-mono">{node.className}</span>}
      subtitle={node.packageName || '(default package)'}
      actions={<button type="button" onClick={onClose} aria-label="Close details" className="text-muted hover:text-fg"><X className="h-4 w-4" aria-hidden /></button>}
    >
      <div className="space-y-4 text-sm">
        <div className="flex flex-wrap gap-1.5">
          <Badge tone="accent">{titleCase(node.componentType)}</Badge>
          {node.module && <Badge>{node.module}</Badge>}
          {node.category && <Badge>{titleCase(node.category)}</Badge>}
          {node.inCycle && <Badge tone="fail">In a cycle</Badge>}
        </div>
        <div>
          <p className="mb-1 text-xs text-muted">Classification confidence</p>
          <Meter value={node.confidence} label="Classification confidence" />
        </div>
        <dl className="grid grid-cols-2 gap-2 text-xs">
          <div><dt className="text-muted">Afferent (Ca)</dt><dd className="font-mono text-fg">{node.afferentCoupling}</dd></div>
          <div><dt className="text-muted">Efferent (Ce)</dt><dd className="font-mono text-fg">{node.efferentCoupling}</dd></div>
        </dl>
        {node.file && <p className="break-all font-mono text-[11px] text-faint">{node.file}</p>}
        <EdgeList title={`Depends on (${outgoing.length})`} edges={outgoing} other={(e) => e.target} name={name} onSelect={onSelect} />
        <EdgeList title={`Used by (${incoming.length})`} edges={incoming} other={(e) => e.source} name={name} onSelect={onSelect} />
      </div>
    </Panel>
  );
}

function EdgeList({ title, edges, other, name, onSelect }: {
  title: string;
  edges: GraphEdge[];
  other: (e: GraphEdge) => string;
  name: (id: string) => string;
  onSelect: (id: string) => void;
}) {
  if (edges.length === 0) {
    return null;
  }
  return (
    <div>
      <h3 className="mb-1 text-xs font-semibold uppercase tracking-wide text-faint">{title}</h3>
      <ul className="max-h-48 space-y-1 overflow-y-auto">
        {edges.map((e) => (
          <li key={e.id} className="flex items-center justify-between gap-2 text-xs">
            <button type="button" className="truncate font-mono text-accent hover:underline" onClick={() => onSelect(other(e))}>{name(other(e))}</button>
            <span className="flex shrink-0 gap-1">
              {e.violation && <Badge tone="warn">violation</Badge>}
              {e.crossModule && <Badge tone="info">cross-module</Badge>}
              <Badge title={`${e.occurrences} occurrence(s), confidence ${Math.round(e.confidence * 100)}%`}>{e.type.toLowerCase().replace(/_/g, ' ')}</Badge>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
