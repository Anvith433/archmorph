import { describe, expect, it } from 'vitest';
import { isBusy, STATE_LABEL, uiState } from './state';
import { formatBytes, shortPackage, titleCase } from './format';

describe('ui state', () => {
  it('maps every server status to a UI state', () => {
    expect(uiState(undefined)).toBe('idle');
    expect(uiState('QUEUED')).toBe('queued');
    expect(uiState('PLANNING')).toBe('analyzing');
    expect(uiState('READY_FOR_REVIEW')).toBe('ready-for-review');
    expect(uiState('FAILED')).toBe('failed');
    expect(STATE_LABEL[uiState('VALIDATING')]).toBe('Validating');
  });

  it('treats running jobs as busy and terminal states as idle', () => {
    expect(isBusy('ANALYZING')).toBe(true);
    expect(isBusy('TRANSFORMING')).toBe(true);
    expect(isBusy('READY_FOR_REVIEW')).toBe(false);
    expect(isBusy('COMPLETED')).toBe(false);
  });
});

describe('format helpers', () => {
  it('formats sizes and names', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB');
    expect(titleCase('MANUAL_REVIEW')).toBe('Manual Review');
    expect(shortPackage('com.acme.modules.user', 'com.acme')).toBe('….modules.user');
    expect(shortPackage('')).toBe('(default package)');
  });
});
