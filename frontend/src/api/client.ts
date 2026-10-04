import type {
  Analysis,
  ArchitectureView,
  CreatedProject,
  Diff,
  DryRun,
  Envelope,
  Graph,
  Job,
  ModuleEdit,
  Modules,
  Plan,
  Project,
  TargetStrategy,
  Validation,
} from './types';

/** API root. Same-origin by default (the Vite dev server proxies /api to the backend). */
export const API_BASE: string = (import.meta.env.VITE_API_BASE as string | undefined) ?? '/api/v1';

/** A failed API call with the server's user-safe message, machine code and hint. */
export class ApiError extends Error {
  readonly code: string;
  readonly hint?: string;
  readonly status: number;
  readonly requestId?: string;

  constructor(message: string, code: string, status: number, hint?: string, requestId?: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.hint = hint;
    this.requestId = requestId;
  }
}

const PROJECT_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const ENTRY_ID = /^e-\d{4,7}$/;

/** Only well-formed IDs are ever interpolated into URLs. */
export function projectPath(projectId: string, suffix = ''): string {
  if (!PROJECT_ID.test(projectId)) {
    throw new ApiError('Invalid project identifier.', 'INVALID_REQUEST', 400);
  }
  return `${API_BASE}/projects/${projectId}${suffix}`;
}

async function parse<T>(response: Response): Promise<T> {
  let body: Envelope<T> | null = null;
  try {
    body = (await response.json()) as Envelope<T>;
  } catch {
    body = null;
  }
  if (!response.ok || !body || !body.success) {
    const retryAfter = response.headers.get('Retry-After');
    throw new ApiError(
      body?.message ?? `The server responded with status ${response.status}.`,
      body?.errorCode ?? (response.status === 429 ? 'RATE_LIMITED' : 'HTTP_' + response.status),
      response.status,
      body?.hint ?? (retryAfter ? `Retry in ${retryAfter} seconds.` : undefined),
      body?.requestId ?? response.headers.get('X-Request-Id') ?? undefined,
    );
  }
  return body.data as T;
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, { ...init, headers: { Accept: 'application/json', ...(init?.headers ?? {}) } });
  } catch {
    throw new ApiError('The ArchMorph server could not be reached.', 'NETWORK_ERROR', 0,
      'Check that the backend is running (default http://localhost:8080) and try again.');
  }
  return parse<T>(response);
}

function json(method: string, body?: unknown): RequestInit {
  return {
    method,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  };
}

/**
 * Upload with progress. fetch() cannot report upload progress, so this uses XMLHttpRequest.
 */
export function uploadProject(file: File, onProgress: (fraction: number) => void): Promise<CreatedProject> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API_BASE}/projects`);
    xhr.responseType = 'json';
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress(event.loaded / event.total);
      }
    };
    xhr.onerror = () =>
      reject(new ApiError('The upload failed: the server could not be reached.', 'NETWORK_ERROR', 0,
        'Check your connection and that the backend is running.'));
    xhr.onload = () => {
      const body = xhr.response as Envelope<CreatedProject> | null;
      if (xhr.status >= 200 && xhr.status < 300 && body?.success && body.data) {
        resolve(body.data);
      } else {
        reject(new ApiError(body?.message ?? `Upload failed with status ${xhr.status}.`,
          body?.errorCode ?? 'HTTP_' + xhr.status, xhr.status, body?.hint, body?.requestId));
      }
    };
    const form = new FormData();
    form.append('file', file);
    xhr.send(form);
  });
}

export const api = {
  project: (id: string) => request<Project>(projectPath(id)),
  job: (jobId: string) => {
    if (!PROJECT_ID.test(jobId)) {
      return Promise.reject(new ApiError('Invalid job identifier.', 'INVALID_REQUEST', 400));
    }
    return request<Job>(`${API_BASE}/jobs/${jobId}`);
  },
  analysis: (id: string) => request<Analysis>(projectPath(id, '/analysis')),
  dependencies: (id: string) => request<Graph>(projectPath(id, '/dependencies')),
  architecture: (id: string) => request<ArchitectureView>(projectPath(id, '/architecture')),
  modules: (id: string) => request<Modules>(projectPath(id, '/modules')),
  updateModules: (id: string, edits: ModuleEdit[]) => request<Modules>(projectPath(id, '/modules'), json('PUT', { edits })),
  plan: (id: string) => request<Plan>(projectPath(id, '/plan')),
  changeStrategy: (id: string, strategy: TargetStrategy) =>
    request<Plan>(projectPath(id, '/strategy'), json('PUT', { strategy })),
  dryRun: (id: string) => request<DryRun>(projectPath(id, '/transform?dryRun=true'), json('POST')),
  transform: (id: string) => request<CreatedProject>(projectPath(id, '/transform'), json('POST')),
  revalidate: (id: string) => request<CreatedProject>(projectPath(id, '/validate'), json('POST')),
  validation: (id: string) => request<Validation>(projectPath(id, '/validation')),
  diff: (id: string, entryId: string) => {
    if (!ENTRY_ID.test(entryId)) {
      return Promise.reject(new ApiError('Invalid plan entry.', 'INVALID_REQUEST', 400));
    }
    return request<Diff>(projectPath(id, `/diff/${entryId}`));
  },
  remove: (id: string) => request<void>(projectPath(id), json('DELETE')),
  downloadUrl: (id: string) => projectPath(id, '/download'),
  reportUrl: (id: string, report: ReportFile) => projectPath(id, `/reports/${report}`),
};

export const REPORTS = [
  'analysis.json',
  'analysis.md',
  'modules.json',
  'transformation-plan.json',
  'transformation-summary.md',
  'validation.json',
  'validation-report.md',
] as const;

export type ReportFile = (typeof REPORTS)[number];
