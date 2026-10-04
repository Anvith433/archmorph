import { RefreshCw, ShieldCheck } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { DownloadPanel } from '../components/DownloadPanel';
import { ErrorPanel } from '../components/ErrorPanel';
import { JobProgress } from '../components/JobProgress';
import { ValidationPanel } from '../components/ValidationPanel';
import { Button, EmptyState, Panel, Skeleton, StatusBadge } from '../components/ui';
import { useProject } from '../hooks/ProjectContext';
import { useResource } from '../hooks/useResource';
import { isBusy } from '../lib/state';

export function ValidationPage() {
  const { projectId, version, project, job, trackJob } = useProject();
  const hasValidation = Boolean(project?.capabilities.hasValidation);
  const validation = useResource(() => api.validation(projectId), hasValidation ? `${projectId}:${version}` : null);
  const [error, setError] = useState<unknown>();
  const [starting, setStarting] = useState(false);
  const busy = isBusy(project?.status);

  const revalidate = async () => {
    setStarting(true);
    setError(undefined);
    try {
      trackJob((await api.revalidate(projectId)).jobId);
    } catch (e) {
      setError(e);
    } finally {
      setStarting(false);
    }
  };

  if (!project) return null;
  const transformJob = job && job.type !== 'ANALYZE' ? job : undefined;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-fg">Validation</h1>
          <p className="max-w-3xl text-sm text-muted">
            Seven levels, from file-system checks to an optional sandboxed Maven compile. Passing validation means the transformed project is structurally consistent and
            compiles where a build was run — it does not prove behaviour is unchanged. Run your own tests.
          </p>
        </div>
        <Button onClick={revalidate} busy={starting} disabled={busy || !project.capabilities.canDownload}>
          <RefreshCw className="h-4 w-4" aria-hidden /> Re-run validation
        </Button>
      </div>

      {error !== undefined && <ErrorPanel error={error} />}

      {transformJob && (busy || transformJob.status === 'FAILED') && (
        <Panel title={<span className="flex items-center gap-2">Progress <StatusBadge status={transformJob.status} /></span>}>
          <JobProgress job={transformJob} />
          {transformJob.error && (
            <div role="alert" className="mt-4 rounded border border-fail/40 bg-fail-soft p-3 text-sm">
              <p className="text-fg">{transformJob.error.message}</p>
              {transformJob.error.hint && <p className="mt-1 text-muted">{transformJob.error.hint}</p>}
            </div>
          )}
        </Panel>
      )}

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[1fr_360px]">
        <div className="min-w-0">
          {!hasValidation && !busy && (
            <EmptyState title="Not transformed yet" icon={<ShieldCheck className="h-8 w-8" aria-hidden />}>
              Review the <Link to="../plan" className="text-accent hover:underline">transformation plan</Link> and start the transformation; validation runs automatically afterwards.
            </EmptyState>
          )}
          {validation.error !== undefined && <ErrorPanel error={validation.error} onRetry={validation.reload} />}
          {hasValidation && !validation.data && validation.error === undefined && <Skeleton className="h-96" />}
          {validation.data && (
            <div className="space-y-3">
              <p className="flex items-center gap-2 text-sm text-muted">
                Overall <StatusBadge status={validation.data.status} /> · completed {new Date(validation.data.completedAt).toLocaleString()}
              </p>
              <ValidationPanel validation={validation.data} />
            </div>
          )}
        </div>
        <DownloadPanel project={project} />
      </div>
    </div>
  );
}
