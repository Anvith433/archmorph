import dagre from '@dagrejs/dagre';
import {
  Background,
  Controls,
  Handle,
  MarkerType,
  Position,
  ReactFlow,
  type Edge,
  type Node,
  type NodeProps,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { memo, useMemo } from 'react';

/** SVG presentation attributes cannot resolve CSS variables, so the background pattern gets a computed colour. */
function token(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}
import type { GraphEdge, GraphNode } from '../api/types';
import { cn } from '../lib/format';

const NODE_WIDTH = 190;
const NODE_HEIGHT = 46;

/** Node colours by component type, expressed as token classes (never colour alone: the type is printed too). */
const TYPE_CLASS: Record<string, string> = {
  CONTROLLER: 'border-l-info',
  SERVICE: 'border-l-accent',
  REPOSITORY: 'border-l-ok',
  ENTITY: 'border-l-warn',
  DTO: 'border-l-faint',
  CONFIGURATION: 'border-l-muted',
  SECURITY: 'border-l-fail',
};

type ClassNodeData = { node: GraphNode; selected: boolean; dimmed: boolean };
type ClassFlowNode = Node<ClassNodeData, 'class'>;

const ClassNode = memo(function ClassNode({ data }: NodeProps<ClassFlowNode>) {
  const { node, selected, dimmed } = data;
  return (
    <div
      className={cn(
        'rounded-md border border-l-4 border-line bg-panel px-2.5 py-1.5 text-left shadow-sm',
        TYPE_CLASS[node.componentType] ?? 'border-l-line-strong',
        selected && 'ring-2 ring-accent',
        node.inCycle && 'border-fail/60',
        dimmed && 'opacity-30',
      )}
      style={{ width: NODE_WIDTH }}
    >
      <Handle type="target" position={Position.Left} className="h-1.5! w-1.5! border-0! bg-line-strong!" />
      <p className="truncate font-mono text-xs font-medium text-fg">{node.className}</p>
      <p className="truncate text-[10px] text-muted">
        {node.componentType.toLowerCase()}
        {node.module ? ` · ${node.module}` : ''}
        {node.inCycle ? ' · cycle' : ''}
      </p>
      <Handle type="source" position={Position.Right} className="h-1.5! w-1.5! border-0! bg-line-strong!" />
    </div>
  );
});

const NODE_TYPES = { class: ClassNode };

/** Deterministic left-to-right layered layout. */
export function layout(nodes: GraphNode[], edges: GraphEdge[]): Map<string, { x: number; y: number }> {
  const g = new dagre.graphlib.Graph();
  g.setGraph({ rankdir: 'LR', nodesep: 18, ranksep: 70, marginx: 10, marginy: 10 });
  g.setDefaultEdgeLabel(() => ({}));
  for (const n of nodes) {
    g.setNode(n.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
  }
  for (const e of edges) {
    if (g.hasNode(e.source) && g.hasNode(e.target) && e.source !== e.target) {
      g.setEdge(e.source, e.target);
    }
  }
  dagre.layout(g);
  const positions = new Map<string, { x: number; y: number }>();
  for (const n of nodes) {
    const p = g.node(n.id);
    positions.set(n.id, { x: (p?.x ?? 0) - NODE_WIDTH / 2, y: (p?.y ?? 0) - NODE_HEIGHT / 2 });
  }
  return positions;
}

export function DependencyGraph({
  nodes,
  edges,
  selectedId,
  onSelect,
}: {
  nodes: GraphNode[];
  edges: GraphEdge[];
  selectedId?: string;
  onSelect: (id: string | undefined) => void;
}) {
  const positions = useMemo(() => layout(nodes, edges), [nodes, edges]);

  const neighbours = useMemo(() => {
    if (!selectedId) {
      return null;
    }
    const set = new Set([selectedId]);
    for (const e of edges) {
      if (e.source === selectedId) set.add(e.target);
      if (e.target === selectedId) set.add(e.source);
    }
    return set;
  }, [edges, selectedId]);

  const flowNodes: ClassFlowNode[] = useMemo(
    () =>
      nodes.map((n) => ({
        id: n.id,
        type: 'class',
        position: positions.get(n.id) ?? { x: 0, y: 0 },
        data: { node: n, selected: n.id === selectedId, dimmed: neighbours !== null && !neighbours.has(n.id) },
        draggable: false,
        connectable: false,
      })),
    [nodes, positions, selectedId, neighbours],
  );

  const flowEdges: Edge[] = useMemo(
    () =>
      edges.map((e) => {
        const highlighted = selectedId !== undefined && (e.source === selectedId || e.target === selectedId);
        const stroke = e.violation ? 'var(--warn)' : e.inCycle ? 'var(--fail)' : highlighted ? 'var(--accent)' : 'var(--border-strong)';
        return {
          id: e.id,
          source: e.source,
          target: e.target,
          animated: e.inCycle,
          style: {
            stroke,
            strokeWidth: highlighted ? 2 : 1.2,
            strokeDasharray: e.crossModule ? '5 4' : undefined,
            opacity: selectedId && !highlighted ? 0.25 : 1,
          },
          markerEnd: { type: MarkerType.ArrowClosed, color: stroke, width: 14, height: 14 },
          ariaLabel: `${e.source} depends on ${e.target} (${e.type})`,
        };
      }),
    [edges, selectedId],
  );

  return (
    <ReactFlow
      nodes={flowNodes}
      edges={flowEdges}
      nodeTypes={NODE_TYPES}
      onNodeClick={(_, node) => onSelect(node.id === selectedId ? undefined : node.id)}
      onPaneClick={() => onSelect(undefined)}
      fitView
      fitViewOptions={{ padding: 0.08, maxZoom: 1.25 }}
      minZoom={0.1}
      maxZoom={2}
      nodesConnectable={false}
      proOptions={{ hideAttribution: true }}
      colorMode={document.documentElement.dataset.theme === 'light' ? 'light' : 'dark'}
    >
      <Background gap={20} size={1} color={token('--border', '#222b3d')} />
      <Controls showInteractive={false} />
    </ReactFlow>
  );
}
