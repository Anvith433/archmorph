import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { BoundarySuggestion } from '../api/types';
import { CyclePanel } from './CyclePanel';

const move: BoundarySuggestion = {
  id: 'b-001', kind: 'MOVE_CLASS', from: 'user', to: 'order', subject: 'com.demo.service.OrderService',
  title: "Move OrderService to module 'order'", rationale: 'r', dependencyCount: 3, steps: ['step'], evidence: ['A → B'],
  edit: { type: 'MOVE_CLASS', className: 'com.demo.service.OrderService', target: 'order' },
};
const facade: BoundarySuggestion = {
  id: 'b-002', kind: 'SPLIT_FACADE', from: 'clinic', to: 'owner, pet', subject: 'x.ClinicService',
  title: 'Split the facade ClinicService by module', rationale: 'r', dependencyCount: 80, steps: ['Move findOwnerById …'], evidence: [],
  edit: null,
};

describe('CyclePanel', () => {
  it('says so when modules are acyclic', () => {
    render(<CyclePanel cycles={[]} suggestions={[]} editable onApply={() => undefined} />);
    expect(screen.getByText(/No cycles between modules/)).toBeInTheDocument();
  });

  it('offers Apply only for suggestions that are module edits', () => {
    const onApply = vi.fn();
    render(<CyclePanel cycles={[['order', 'user']]} suggestions={[move, facade]} editable onApply={onApply} />);
    const buttons = screen.getAllByRole('button', { name: 'Apply' });
    expect(buttons).toHaveLength(1);
    fireEvent.click(buttons[0]);
    expect(onApply).toHaveBeenCalledWith(move.edit);
    expect(screen.getByText('Split the facade ClinicService by module')).toBeInTheDocument();
  });
});
