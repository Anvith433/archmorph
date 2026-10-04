import { lazy, Suspense } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { Spinner } from './components/ui';
import { Landing } from './pages/Landing';
import { NewProject } from './pages/NewProject';
import { NotFound } from './pages/NotFound';
import { Overview } from './pages/Overview';
import { ProjectLayout } from './pages/ProjectLayout';

// Heavier views (graph layout, diff engine) are split into their own chunks.
const Architecture = lazy(() => import('./pages/Architecture').then((m) => ({ default: m.Architecture })));
const Dependencies = lazy(() => import('./pages/Dependencies').then((m) => ({ default: m.Dependencies })));
const Modules = lazy(() => import('./pages/Modules').then((m) => ({ default: m.Modules })));
const PlanPage = lazy(() => import('./pages/PlanPage').then((m) => ({ default: m.PlanPage })));
const DiffPage = lazy(() => import('./pages/DiffPage').then((m) => ({ default: m.DiffPage })));
const ValidationPage = lazy(() => import('./pages/ValidationPage').then((m) => ({ default: m.ValidationPage })));

function Lazy({ children }: { children: React.ReactNode }) {
  return <Suspense fallback={<Spinner label="Loading view" />}>{children}</Suspense>;
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/projects/new" element={<NewProject />} />
      <Route path="/projects/:projectId" element={<ProjectLayout />}>
        <Route index element={<Overview />} />
        <Route path="architecture" element={<Lazy><Architecture /></Lazy>} />
        <Route path="dependencies" element={<Lazy><Dependencies /></Lazy>} />
        <Route path="modules" element={<Lazy><Modules /></Lazy>} />
        <Route path="plan" element={<Lazy><PlanPage /></Lazy>} />
        <Route path="diff" element={<Lazy><DiffPage /></Lazy>} />
        <Route path="validation" element={<Lazy><ValidationPage /></Lazy>} />
        <Route path="*" element={<NotFound inline />} />
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}

export function App() {
  return (
    <BrowserRouter>
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-panel focus:px-3 focus:py-2 focus:text-fg">
        Skip to content
      </a>
      <AppRoutes />
    </BrowserRouter>
  );
}
