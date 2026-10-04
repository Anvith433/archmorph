import type { ProjectStatus } from '../api/types';

/** UI state machine of a project, as shown to the user. */
export type UiState =
  | 'idle'
  | 'uploading'
  | 'queued'
  | 'analyzing'
  | 'ready-for-review'
  | 'transforming'
  | 'validating'
  | 'completed'
  | 'failed';

export function uiState(status: ProjectStatus | undefined): UiState {
  switch (status) {
    case undefined:
      return 'idle';
    case 'QUEUED':
      return 'queued';
    case 'ANALYZING':
    case 'PLANNING':
      return 'analyzing';
    case 'READY_FOR_REVIEW':
      return 'ready-for-review';
    case 'TRANSFORMING':
      return 'transforming';
    case 'VALIDATING':
      return 'validating';
    case 'COMPLETED':
      return 'completed';
    case 'FAILED':
      return 'failed';
  }
}

export function isBusy(status: ProjectStatus | undefined): boolean {
  const state = uiState(status);
  return state === 'queued' || state === 'analyzing' || state === 'transforming' || state === 'validating';
}

export const STATE_LABEL: Record<UiState, string> = {
  idle: 'Idle',
  uploading: 'Uploading',
  queued: 'Queued',
  analyzing: 'Analyzing',
  'ready-for-review': 'Ready for review',
  transforming: 'Transforming',
  validating: 'Validating',
  completed: 'Completed',
  failed: 'Failed',
};
