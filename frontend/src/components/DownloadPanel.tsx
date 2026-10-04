import { Download, FileJson, FileText } from 'lucide-react';
import { api, REPORTS, type ReportFile } from '../api/client';
import type { Project } from '../api/types';
import { Panel } from './ui';

const LABELS: Record<ReportFile, string> = {
  'analysis.json': 'Analysis report',
  'analysis.md': 'Analysis report',
  'modules.json': 'Modules',
  'transformation-plan.json': 'Transformation plan',
  'transformation-summary.md': 'Transformation summary',
  'validation.json': 'Validation report',
  'validation-report.md': 'Validation report',
};

export function DownloadPanel({ project }: { project: Project }) {
  const available = (report: ReportFile) =>
    report.startsWith('validation') ? project.capabilities.hasValidation : project.capabilities.hasPlan || report.startsWith('analysis');
  return (
    <Panel title="Downloads" subtitle="Machine-readable JSON for tooling, Markdown for people.">
      <div className="space-y-3">
        {project.capabilities.canDownload ? (
          <a href={api.downloadUrl(project.projectId)} className="flex items-center justify-between rounded-md border border-accent/40 bg-accent-soft px-3 py-2.5 text-sm font-medium text-fg hover:bg-accent/20" download>
            <span className="flex items-center gap-2"><Download className="h-4 w-4 text-accent" aria-hidden /> Download transformed project</span>
            <span className="text-xs text-muted">ZIP</span>
          </a>
        ) : (
          <p className="rounded-md border border-dashed border-line px-3 py-2.5 text-sm text-muted">The transformed project becomes available after transformation and validation.</p>
        )}
        <ul className="divide-y divide-line rounded-md border border-line">
          {REPORTS.map((report) => (
            <li key={report} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
              <span className="flex min-w-0 items-center gap-2 text-fg">
                {report.endsWith('.json') ? <FileJson className="h-4 w-4 shrink-0 text-faint" aria-hidden /> : <FileText className="h-4 w-4 shrink-0 text-faint" aria-hidden />}
                <span className="min-w-0">
                  <span className="block truncate">{LABELS[report]}</span>
                  <span className="block truncate font-mono text-[11px] text-faint">{report}</span>
                </span>
              </span>
              {available(report) ? (
                <a className="shrink-0 text-xs font-medium text-accent hover:underline" href={api.reportUrl(project.projectId, report)} download>
                  Download
                </a>
              ) : (
                <span className="shrink-0 text-xs text-faint">Not yet available</span>
              )}
            </li>
          ))}
        </ul>
      </div>
    </Panel>
  );
}
