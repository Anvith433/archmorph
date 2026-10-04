import { FileArchive, Info, ShieldCheck, UploadCloud, X } from 'lucide-react';
import { useRef, useState, type DragEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError, uploadProject } from '../api/client';
import { ErrorPanel } from '../components/ErrorPanel';
import { Topbar } from '../components/Topbar';
import { Button } from '../components/ui';
import { cn, formatBytes } from '../lib/format';

export const MAX_ARCHIVE_BYTES = 100 * 1024 * 1024;

export function validateArchive(file: File): string | null {
  if (!file.name.toLowerCase().endsWith('.zip')) {
    return 'Only .zip archives are supported.';
  }
  if (file.size === 0) {
    return 'The selected file is empty.';
  }
  if (file.size > MAX_ARCHIVE_BYTES) {
    return `The archive is ${formatBytes(file.size)}; the maximum is ${formatBytes(MAX_ARCHIVE_BYTES)}. Remove target/ and node_modules/ and try again.`;
  }
  return null;
}

export function NewProject() {
  const navigate = useNavigate();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<unknown>(null);

  const choose = (candidate: File | undefined) => {
    setError(null);
    if (!candidate) {
      return;
    }
    const issue = validateArchive(candidate);
    setProblem(issue);
    setFile(issue ? null : candidate);
  };

  const onDrop = (event: DragEvent) => {
    event.preventDefault();
    setDragging(false);
    choose(event.dataTransfer.files[0]);
  };

  const start = async () => {
    if (!file) {
      return;
    }
    setProgress(0);
    setError(null);
    try {
      const created = await uploadProject(file, setProgress);
      navigate(`/projects/${created.projectId}`);
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError('The upload failed.', 'UPLOAD_FAILED', 0));
      setProgress(null);
    }
  };

  return (
    <div className="min-h-screen">
      <Topbar />
      <main id="main" className="mx-auto max-w-3xl px-6 py-12">
        <h1 className="text-2xl font-semibold text-fg">Analyze a project</h1>
        <p className="mt-1 text-sm text-muted">Upload a ZIP of a Maven-based Java or Spring Boot project. Nothing is changed until you approve a transformation.</p>

        <div
          onDragOver={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          className={cn(
            'mt-8 flex flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 py-14 text-center transition-colors',
            dragging ? 'border-accent bg-accent-soft' : 'border-line-strong bg-panel',
          )}
        >
          <UploadCloud className="h-10 w-10 text-accent" aria-hidden />
          <p className="mt-3 text-sm text-fg">Drag and drop your project ZIP here</p>
          <p className="mt-1 text-xs text-muted">or</p>
          <Button className="mt-3" onClick={() => input.current?.click()}>Choose a file</Button>
          <input
            ref={input}
            type="file"
            accept=".zip,application/zip"
            className="sr-only"
            aria-label="Project archive"
            onChange={(e) => choose(e.target.files?.[0] ?? undefined)}
          />
        </div>

        {problem && <p role="alert" className="mt-3 text-sm text-fail">{problem}</p>}

        {file && (
          <div className="mt-4 flex items-center gap-3 rounded-lg border border-line bg-panel px-4 py-3">
            <FileArchive className="h-5 w-5 text-muted" aria-hidden />
            <div className="min-w-0 flex-1">
              {/* the name is rendered as text: React escapes it */}
              <p className="truncate text-sm text-fg">{file.name}</p>
              <p className="text-xs text-muted">{formatBytes(file.size)}</p>
            </div>
            {progress === null && (
              <button type="button" onClick={() => setFile(null)} aria-label="Remove file" className="rounded p-1 text-muted hover:text-fg">
                <X className="h-4 w-4" aria-hidden />
              </button>
            )}
          </div>
        )}

        {progress !== null && (
          <div className="mt-4" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(progress * 100)} aria-label="Upload progress">
            <div className="h-1.5 overflow-hidden rounded-full bg-sunken">
              <div className="h-full bg-accent transition-all" style={{ width: `${progress * 100}%` }} />
            </div>
            <p className="mt-1 text-xs text-muted">{progress < 1 ? `Uploading… ${Math.round(progress * 100)}%` : 'Upload complete — queuing analysis…'}</p>
          </div>
        )}

        {error !== null && <div className="mt-4"><ErrorPanel error={error} title="Upload failed" /></div>}

        <div className="mt-6 flex justify-end">
          <Button variant="primary" disabled={!file} busy={progress !== null} onClick={start}>
            Upload and analyze
          </Button>
        </div>

        <ul className="mt-10 grid gap-3 text-sm sm:grid-cols-3">
          <li className="flex gap-2 rounded-lg border border-line bg-panel p-3 text-muted"><Info className="h-4 w-4 shrink-0 text-info" aria-hidden />Maximum archive size: 100 MB</li>
          <li className="flex gap-2 rounded-lg border border-line bg-panel p-3 text-muted"><Info className="h-4 w-4 shrink-0 text-info" aria-hidden />Java/Maven projects supported (Gradle not yet)</li>
          <li className="flex gap-2 rounded-lg border border-line bg-panel p-3 text-muted"><ShieldCheck className="h-4 w-4 shrink-0 text-ok" aria-hidden />Every project gets an isolated workspace; generated files never touch your original</li>
        </ul>
      </main>
    </div>
  );
}
