import { Boxes, FileDiff, GitBranch, LayoutDashboard, ListChecks, Network, ShieldCheck } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { cn } from '../lib/format';

const ITEMS = [
  { to: '', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: 'architecture', label: 'Architecture', icon: GitBranch },
  { to: 'dependencies', label: 'Dependencies', icon: Network },
  { to: 'modules', label: 'Modules', icon: Boxes },
  { to: 'plan', label: 'Plan', icon: ListChecks },
  { to: 'diff', label: 'Diff', icon: FileDiff },
  { to: 'validation', label: 'Validation', icon: ShieldCheck },
];

export function Sidebar({ disabled }: { disabled: boolean }) {
  return (
    <nav aria-label="Project" className="relative flex gap-1 overflow-x-auto border-b border-line bg-elevated px-2 py-2 lg:w-52 lg:shrink-0 lg:flex-col lg:overflow-visible lg:border-b-0 lg:border-r lg:px-3 lg:py-4">
      {ITEMS.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={label}
          to={to}
          end={end}
          aria-disabled={disabled && to !== ''}
          onClick={(e) => disabled && to !== '' && e.preventDefault()}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-2.5 whitespace-nowrap rounded-md px-2.5 py-1.5 text-sm transition-colors',
              isActive ? 'bg-accent-soft font-medium text-fg' : 'text-muted hover:bg-panel-hover hover:text-fg',
              disabled && to !== '' && 'pointer-events-auto cursor-not-allowed opacity-40 hover:bg-transparent',
            )
          }
        >
          <Icon className="h-4 w-4 shrink-0" aria-hidden />
          {label}
        </NavLink>
      ))}
    </nav>
  );
}
