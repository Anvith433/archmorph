import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import type { Graph } from '../api/types';
import { ErrorPanel } from '../components/ErrorPanel';
import { filterGraph } from './Dependencies';
import { describeEdit } from './Modules';
import { MAX_ARCHIVE_BYTES, validateArchive } from './NewProject';
import { AppRoutes } from '../App';

const file = (name: string, size: number) => {
  const f = new File(['x'], name);
  Object.defineProperty(f, 'size', { value: size });
  return f;
};

describe('validateArchive', () => {
  it('accepts a zip within the limit', () => expect(validateArchive(file('app.zip', 1000))).toBeNull());
  it('rejects other formats', () => expect(validateArchive(file('app.tar.gz', 1000))).toMatch(/Only .zip/));
  it('rejects empty files', () => expect(validateArchive(file('app.zip', 0))).toMatch(/empty/));
  it('rejects oversized archives', () => expect(validateArchive(file('APP.ZIP', MAX_ARCHIVE_BYTES + 1))).toMatch(/maximum/));
});

const node = (id: string, componentType: string, extra: Partial<Graph['nodes'][number]> = {}) => ({
  id, className: id.split('.').pop() ?? id, packageName: 'p', componentType, confidence: 1, afferentCoupling: 0, efferentCoupling: 0, inCycle: false, ...extra,
});
const edge = (source: string, target: string, extra: Partial<Graph['edges'][number]> = {}) => ({
  id: `${source}->${target}`, source, target, type: 'FIELD', occurrences: 1, confidence: 1, crossModule: false, violation: false, inCycle: false, ...extra,
});
const graph: Graph = {
  nodes: [node('p.UserController', 'CONTROLLER'), node('p.UserService', 'SERVICE'), node('p.UserRepository', 'REPOSITORY'), node('p.Util', 'UNKNOWN')],
  edges: [
    edge('p.UserController', 'p.UserService'),
    edge('p.UserService', 'p.UserRepository', { crossModule: true }),
    edge('p.UserController', 'p.UserRepository', { violation: true }),
    edge('p.UserService', 'p.Util'),
  ],
  totalNodes: 4, totalEdges: 4, truncated: false,
};
const ALL = new Set(['CONTROLLER', 'SERVICE', 'REPOSITORY', 'ENTITY', 'DTO', 'CONFIGURATION', 'SECURITY', 'OTHER'] as const);

describe('filterGraph', () => {
  it('filters by component type, mapping unknown types to OTHER', () => {
    const types = new Set(ALL);
    types.delete('OTHER');
    const out = filterGraph(graph, { types, crossModuleOnly: false, cyclesOnly: false, violationsOnly: false, query: '' });
    expect(out.nodes.map((n) => n.id)).not.toContain('p.Util');
    expect(out.edges).toHaveLength(3);
  });

  it('keeps only violating edges and their endpoints', () => {
    const out = filterGraph(graph, { types: ALL, crossModuleOnly: false, cyclesOnly: false, violationsOnly: true, query: '' });
    expect(out.edges.map((e) => e.id)).toEqual(['p.UserController->p.UserRepository']);
    expect(out.nodes).toHaveLength(2);
  });

  it('search keeps matches and their direct neighbours', () => {
    const out = filterGraph(graph, { types: ALL, crossModuleOnly: false, cyclesOnly: false, violationsOnly: false, query: 'util' });
    expect(out.nodes.map((n) => n.id).sort()).toEqual(['p.UserService', 'p.Util']);
  });
});

describe('describeEdit', () => {
  it('describes decisions in plain language', () => {
    expect(describeEdit({ type: 'MOVE_CLASS', className: 'com.acme.user.UserMapper', target: 'order' })).toBe('Move UserMapper → order');
    expect(describeEdit({ type: 'MERGE_MODULES', sources: ['billing'], target: 'payment' })).toBe('Merge billing into payment');
    expect(describeEdit({ type: 'LOCK_CLASS', className: 'a.B' })).toBe('Lock B');
  });
});

describe('ErrorPanel', () => {
  it('shows reason, hint and request id but nothing else', () => {
    render(<ErrorPanel error={new ApiError('Archive rejected.', 'ZIP_SECURITY', 400, 'Re-create the zip.', 'req-9')} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Archive rejected.');
    expect(screen.getByText(/Re-create the zip/)).toBeInTheDocument();
    expect(screen.getByText(/req-9/)).toBeInTheDocument();
  });

  it('does not leak the message of unexpected browser errors', () => {
    render(<ErrorPanel error={new Error('TypeError at /home/user/secret.js:1')} />);
    expect(screen.getByRole('alert')).not.toHaveTextContent('/home/user');
  });
});

describe('routing', () => {
  it('renders the landing page and a not-found page', () => {
    const { unmount } = render(<MemoryRouter initialEntries={['/']}><AppRoutes /></MemoryRouter>);
    expect(screen.getAllByText(/ArchMorph/).length).toBeGreaterThan(0);
    unmount();
    render(<MemoryRouter initialEntries={['/nope']}><AppRoutes /></MemoryRouter>);
    expect(screen.getByText('Page not found')).toBeInTheDocument();
  });
});
