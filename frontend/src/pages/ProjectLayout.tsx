import { Trash2 } from 'lucide-react';
import { useState } from 'react';
import { Outlet, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client';
import { ErrorPanel } from '../components/ErrorPanel';
import { Sidebar } from '../components/Sidebar';
import { Topbar } from '../components/Topbar';
import { Button, Spinner, StatusBadge } from '../components/ui';
import { ProjectProvider, useProject } from '../hooks/ProjectContext';

function ProjectChrome() {
  const { project, error } = useProject();
  const navigate = useNavigate();
  const [deleting, setDeleting] = useState(false);
  const analysed = project && project.capabilities.hasPlan;

  const remove = async () => {
    if (!project || !window.confirm('Delete this project and all generated files? This cannot be undone.')) {
      return;
    }
    setDeleting(true);
    try {
      await api.remove(project.projectId);
      navigate('/projects/new');
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col">
      <Topbar>
        {project && (
          <div className="flex min-w-0 items-center gap-3">
            <span className="hidden text-sm text-muted sm:inline">/</span>
            <span className="min-w-0 truncate text-sm font-medium text-fg">{project.name}</span>
            <StatusBadge status={project.status} className="shrink-0" />
          </div>
        )}
      </Topbar>
      <div className="flex flex-1 flex-col lg:flex-row">
        <Sidebar disabled={!analysed} />
        <main id="main" className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-8">
          {error !== undefined && !project && <ErrorPanel error={error} title="The project could not be loaded" />}
          {!project && error === undefined && <Spinner label="Loading project" />}
          {project && <Outlet />}
        </main>
      </div>
      {project && (
        <footer className="flex items-center justify-between border-t border-line px-4 py-2 text-xs text-faint">
          <span>Project files are deleted automatically on {new Date(project.expiresAt).toLocaleString()}.</span>
          <Button size="sm" variant="ghost" busy={deleting} onClick={remove}>
            <Trash2 className="h-3.5 w-3.5" aria-hidden /> Delete now
          </Button>
        </footer>
      )}
    </div>
  );
}

export function ProjectLayout() {
  const { projectId } = useParams();
  if (!projectId) {
    return null;
  }
  return (
    <ProjectProvider key={projectId} projectId={projectId}>
      <ProjectChrome />
    </ProjectProvider>
  );
}
