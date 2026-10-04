import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { api, ApiError } from '../api/client';
import { progressSource } from '../api/progress';
import type { Job, Project } from '../api/types';
import { isBusy } from '../lib/state';

interface ProjectState {
  projectId: string;
  project?: Project;
  job?: Job;
  error?: unknown;
  /** Increments whenever server-side data may have changed; views reload on change. */
  version: number;
  refresh: () => void;
  /** Start tracking a job (after transform/validate) and refresh when it finishes. */
  trackJob: (jobId: string) => void;
}

const Context = createContext<ProjectState | null>(null);

export function ProjectProvider({ projectId, children }: { projectId: string; children: ReactNode }) {
  const [project, setProject] = useState<Project>();
  const [job, setJob] = useState<Job>();
  const [error, setError] = useState<unknown>();
  const [version, setVersion] = useState(0);
  const [jobId, setJobId] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);

  const load = useCallback(async () => {
    try {
      const next = await api.project(projectId);
      setProject((previous) => {
        if (previous && previous.status !== next.status) {
          setVersion((v) => v + 1);
        }
        return next;
      });
      setError(undefined);
      if (next.latestJobId) {
        setJobId((current) => current ?? next.latestJobId ?? null);
      }
      if (isBusy(next.status)) {
        timer.current = setTimeout(load, 1500);
      }
    } catch (e) {
      setError(e);
      // Keep polling through transient outages (backend restart, network blip); stop on real errors such as 404.
      if (e instanceof ApiError && (e.status === 0 || e.status >= 500 || e.status === 429)) {
        timer.current = setTimeout(load, 5000);
      }
    }
  }, [projectId]);

  useEffect(() => {
    void load();
    return () => clearTimeout(timer.current);
  }, [load]);

  useEffect(() => {
    if (!jobId) {
      return;
    }
    return progressSource.subscribe(
      jobId,
      (update) => {
        setJob(update);
        if (update.status === 'COMPLETED' || update.status === 'FAILED') {
          clearTimeout(timer.current);
          void load();
          setVersion((v) => v + 1);
        }
      },
      () => undefined,
    );
  }, [jobId, load]);

  const refresh = useCallback(() => {
    clearTimeout(timer.current);
    setVersion((v) => v + 1);
    void load();
  }, [load]);

  const trackJob = useCallback(
    (id: string) => {
      setJob(undefined);
      setJobId(id);
      clearTimeout(timer.current);
      void load();
    },
    [load],
  );

  return (
    <Context.Provider value={{ projectId, project, job, error, version, refresh, trackJob }}>{children}</Context.Provider>
  );
}

export function useProject(): ProjectState {
  const value = useContext(Context);
  if (!value) {
    throw new Error('useProject must be used inside ProjectProvider');
  }
  return value;
}
