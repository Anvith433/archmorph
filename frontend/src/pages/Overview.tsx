import { ArrowRight, Boxes } from 'lucide-react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { DownloadPanel } from '../components/DownloadPanel';
import { ErrorPanel } from '../components/ErrorPanel';
import { JobProgress } from '../components/JobProgress';
import { Indicator, MetricCard } from '../components/MetricCard';
import { Badge, Panel, Skeleton, StatusBadge } from '../components/ui';
import { useProject } from '../hooks/ProjectContext';
import { useResource } from '../hooks/useResource';
import { isBusy } from '../lib/state';

export function Overview() {
  const { project, job, projectId, version } = useProject();
  const analysed = Boolean(project?.capabilities.hasPlan);
  const analysis = useResource(() => api.analysis(projectId), analysed ? `${projectId}:${version}` : null);

  if (!project) {
    return null;
  }

  if (project.status === 'FAILED' && !analysed) {
    return (
      <div className="max-w-3xl space-y-4">
        <h1 className="text-xl font-semibold text-fg">Analysis failed</h1>
        <FailureDetails project={project} />
        {job && (
          <Panel title="What happened">
            <JobProgress job={job} />
          </Panel>
        )}
        <Link to="/projects/new" className="inline-flex items-center gap-1.5 text-sm font-medium text-accent hover:underline">
          Upload a different archive <ArrowRight className="h-4 w-4" aria-hidden />
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold text-fg">Overview</h1>
          <p className="text-sm text-muted">Static-analysis summary of {project.name}.</p>
        </div>
        {analysed && (
          <Link to="modules" className="inline-flex items-center gap-1.5 text-sm font-medium text-accent hover:underline">
            Review modules <ArrowRight className="h-4 w-4" aria-hidden />
          </Link>
        )}
      </div>

      {project.status === 'FAILED' && <FailureDetails project={project} />}

      {(isBusy(project.status) || (job && job.status !== 'COMPLETED')) && job && (
        <Panel title={<span className="flex items-center gap-2">Job progress <StatusBadge status={job.status} /></span>}>
          <JobProgress job={job} />
          {job.error && <div className="mt-4"><FailureDetails project={{ ...project, failure: job.error }} /></div>}
        </Panel>
      )}

      {analysis.error !== undefined && <ErrorPanel error={analysis.error} onRetry={analysis.reload} />}

      {analysed && !analysis.data && !analysis.error && (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">{Array.from({ length: 10 }, (_, i) => <Skeleton key={i} className="h-20" />)}</div>
      )}

      {analysis.data && (
        <>
          <section aria-label="Metrics" className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
            <MetricCard label="Java classes" value={analysis.data.counts.classes} hint={`${analysis.data.counts.javaFiles} main / ${analysis.data.counts.testFiles} test files`} />
            <MetricCard label="Dependencies" value={analysis.data.counts.dependencies} />
            <MetricCard label="Controllers" value={analysis.data.counts.controllers} />
            <MetricCard label="Services" value={analysis.data.counts.services} />
            <MetricCard label="Repositories" value={analysis.data.counts.repositories} />
            <MetricCard label="Entities" value={analysis.data.counts.entities} />
            <MetricCard label="Cycles" value={analysis.data.counts.cycles} tone={analysis.data.counts.cycles ? 'warn' : undefined} />
            <MetricCard label="Layer violations" value={analysis.data.counts.layerViolations} tone={analysis.data.counts.layerViolations ? 'warn' : undefined} />
            <MetricCard label="Business modules" value={analysis.data.counts.businessModules} />
            <MetricCard label="Unclassified" value={analysis.data.counts.unknown} tone={analysis.data.counts.unknown ? 'warn' : undefined} />
          </section>

          <section aria-label="Indicators" className="space-y-2">
            <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
              <Indicator label="Architecture health" value={analysis.data.indicators.architectureHealth} description="Penalises layer violations, cycles and unclassified classes." />
              <Indicator label="Module confidence" value={analysis.data.indicators.moduleConfidence} description="Class-weighted confidence of the suggested business modules." />
              <Indicator label="Transformation readiness" value={analysis.data.indicators.transformationReadiness} description="Share of files that can move automatically, reduced by conflicts." />
            </div>
            <p className="text-xs text-faint">{analysis.data.indicators.note}</p>
          </section>

          <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
            <div className="space-y-6 xl:col-span-2">
              <Panel title="Project" subtitle="What ArchMorph detected.">
                <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                  <div><dt className="text-xs text-muted">Build tool</dt><dd className="text-fg">{analysis.data.project.buildTool}</dd></div>
                  <div><dt className="text-xs text-muted">Spring Boot</dt><dd className="text-fg">{analysis.data.project.springBoot ? 'Yes' : 'No'}</dd></div>
                  <div><dt className="text-xs text-muted">Package style</dt><dd className="text-fg">{analysis.data.architecture.packageStyle}</dd></div>
                  <div><dt className="text-xs text-muted">Avg. instability</dt><dd className="font-mono text-fg">{analysis.data.architecture.averageInstability.toFixed(2)}</dd></div>
                </dl>
                {[...analysis.data.project.notes, ...analysis.data.architecture.warnings].length > 0 && (
                  <ul className="mt-4 space-y-1.5 text-sm">
                    {[...analysis.data.project.notes, ...analysis.data.architecture.warnings].map((note) => (
                      <li key={note} className="flex gap-2 text-muted"><Badge tone="warn">Note</Badge>{note}</li>
                    ))}
                  </ul>
                )}
              </Panel>
              {analysis.data.cycles.length > 0 && (
                <Panel title="Dependency cycles" subtitle="Reported, never rewritten automatically — breaking a cycle is an architectural decision.">
                  <ul className="space-y-3">
                    {analysis.data.cycles.map((cycle) => (
                      <li key={cycle.cycleId} className="rounded-md border border-line bg-sunken p-3">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-xs text-faint">{cycle.cycleId}</span>
                          <StatusBadge status={cycle.severity} />
                          <span className="text-xs text-muted">{cycle.edgeCount} dependencies · {cycle.dependencyTypes.join(', ')}</span>
                        </div>
                        <p className="mt-2 font-mono text-xs text-fg">{cycle.path.join(' → ')}</p>
                        <p className="mt-1 text-xs text-muted">{cycle.recommendation}</p>
                      </li>
                    ))}
                  </ul>
                </Panel>
              )}
              {analysis.data.violations.length > 0 && (
                <Panel title="Layer violations" subtitle={`${analysis.data.violations.length} dependencies break the layer rules.`}>
                  <ul className="divide-y divide-line text-sm">
                    {analysis.data.violations.slice(0, 20).map((v) => (
                      <li key={`${v.source}-${v.target}-${v.dependencyType}`} className="flex flex-wrap items-center gap-2 py-2">
                        <StatusBadge status={v.severity} />
                        <span className="text-fg">{v.message}</span>
                        {v.file && <span className="font-mono text-xs text-faint">{v.file}:{v.line}</span>}
                      </li>
                    ))}
                  </ul>
                </Panel>
              )}
            </div>
            <div className="space-y-6">
              <DownloadPanel project={project} />
              <Panel title="Next steps">
                <ol className="space-y-2 text-sm text-muted">
                  <li><Link className="text-accent hover:underline" to="modules"><Boxes className="mr-1 inline h-4 w-4" aria-hidden />Review the suggested modules</Link></li>
                  <li><Link className="text-accent hover:underline" to="plan">Inspect the transformation plan</Link></li>
                  <li><Link className="text-accent hover:underline" to="diff">Read the source diffs</Link></li>
                  <li><Link className="text-accent hover:underline" to="validation">Transform and validate</Link></li>
                </ol>
              </Panel>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

function FailureDetails({ project }: { project: { failure?: { code: string; message: string; hint?: string | null } | null } }) {
  if (!project.failure) {
    return null;
  }
  return (
    <div role="alert" className="rounded-lg border border-fail/40 bg-fail-soft p-4 text-sm">
      <p className="font-semibold text-fg">The last job failed</p>
      <p className="mt-2 text-muted"><span className="font-medium text-fg">Reason:</span> {project.failure.message}</p>
      {project.failure.hint && <p className="mt-1 text-muted"><span className="font-medium text-fg">What you can do:</span> {project.failure.hint}</p>}
      <p className="mt-2 font-mono text-[11px] text-faint">{project.failure.code}</p>
    </div>
  );
}
