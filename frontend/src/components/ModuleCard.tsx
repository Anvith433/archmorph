import { GripVertical, Lock, LockOpen, Share2, Ban, Undo2 } from 'lucide-react';
import { useState, type DragEvent } from 'react';
import type { Module, ModuleClass, ModuleEdit } from '../api/types';
import { cn, titleCase } from '../lib/format';
import { Badge, Meter, type Tone } from './ui';

export const DRAG_MIME = 'application/x-archmorph-class';

const CATEGORY_TONE: Record<string, Tone> = {
  BUSINESS_MODULE: 'accent',
  SHARED: 'info',
  INFRASTRUCTURE: 'neutral',
  CONFIGURATION: 'neutral',
  SECURITY: 'warn',
  APPLICATION: 'neutral',
  UNKNOWN: 'fail',
};

/**
 * One module with its classes. Classes can be dragged onto another card, or moved with the
 * "Move to" select (the keyboard alternative to drag and drop).
 */
export function ModuleCard({
  module,
  moduleNames,
  editable,
  onEdit,
  showExposure = false,
}: {
  module: Module;
  moduleNames: string[];
  editable: boolean;
  onEdit: (edit: ModuleEdit) => void;
  /** MODULAR_MONOLITH layout: show and edit whether each class is part of the module's public API. */
  showExposure?: boolean;
}) {
  const [dropping, setDropping] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [newName, setNewName] = useState(module.name);
  const [splitting, setSplitting] = useState(false);
  const [splitName, setSplitName] = useState('');
  const [splitClasses, setSplitClasses] = useState<Set<string>>(new Set());
  const business = module.category === 'BUSINESS_MODULE';
  const others = moduleNames.filter((n) => n !== module.name);

  const onDrop = (event: DragEvent) => {
    event.preventDefault();
    setDropping(false);
    const className = event.dataTransfer.getData(DRAG_MIME);
    if (className && !module.classes.some((c) => c.qualifiedName === className)) {
      onEdit(module.name === 'shared' ? { type: 'MOVE_TO_SHARED', className } : { type: 'MOVE_CLASS', className, target: module.name });
    }
  };

  return (
    <section
      aria-label={`Module ${module.name}`}
      onDragOver={(e) => {
        if (editable && e.dataTransfer.types.includes(DRAG_MIME) && module.name !== 'application') {
          e.preventDefault();
          setDropping(true);
        }
      }}
      onDragLeave={() => setDropping(false)}
      onDrop={onDrop}
      className={cn('flex flex-col rounded-lg border bg-panel', dropping ? 'border-accent ring-2 ring-accent/40' : 'border-line')}
    >
      <header className="space-y-2 border-b border-line px-4 py-3">
        <div className="flex flex-wrap items-center gap-2">
          {renaming ? (
            <form
              className="flex items-center gap-1.5"
              onSubmit={(e) => {
                e.preventDefault();
                setRenaming(false);
                if (newName && newName !== module.name) onEdit({ type: 'RENAME_MODULE', module: module.name, newName });
              }}
            >
              <label className="sr-only" htmlFor={`rename-${module.name}`}>New module name</label>
              <input
                id={`rename-${module.name}`}
                value={newName}
                onChange={(e) => setNewName(e.target.value.toLowerCase())}
                pattern="[a-z][a-z0-9]*"
                maxLength={40}
                autoFocus
                className="h-7 w-36 rounded border border-line bg-sunken px-2 font-mono text-sm text-fg focus:border-accent focus:outline-none"
              />
              <button type="submit" className="text-xs font-medium text-accent">Save</button>
              <button type="button" className="text-xs text-muted" onClick={() => setRenaming(false)}>Cancel</button>
            </form>
          ) : (
            <h3 className="font-mono text-sm font-semibold text-fg">{module.name}</h3>
          )}
          <Badge tone={CATEGORY_TONE[module.category] ?? 'neutral'}>{titleCase(module.category)}</Badge>
          <span className="ml-auto text-xs text-faint">{module.classCount} classes</span>
        </div>
        {business && (
          <div className="grid grid-cols-3 gap-3 text-[11px] text-muted">
            <div>Confidence<Meter value={module.confidence} label={`${module.name} confidence`} /></div>
            <div>Cohesion<Meter value={module.cohesion} label={`${module.name} cohesion`} /></div>
            <div title="Share of dependencies leaving the module (lower is better)">
              Ext. coupling<Meter value={module.externalCoupling} label={`${module.name} external coupling`} tone={module.externalCoupling > 0.5 ? 'warn' : 'ok'} />
            </div>
          </div>
        )}
        {module.warnings.map((w) => (
          <p key={w} className="flex gap-1.5 text-xs text-warn"><Badge tone="warn">Warning</Badge>{w}</p>
        ))}
        {editable && business && (
          <div className="flex flex-wrap gap-2 text-xs">
            <button type="button" className="text-accent hover:underline" onClick={() => { setNewName(module.name); setRenaming(true); }}>Rename</button>
            <label className="flex items-center gap-1 text-muted">
              Merge into
              <select
                className="h-6 rounded border border-line bg-sunken px-1 text-xs text-fg"
                value=""
                onChange={(e) => e.target.value && onEdit({ type: 'MERGE_MODULES', sources: [module.name], target: e.target.value })}
              >
                <option value="">…</option>
                {others.filter((n) => n !== 'shared' && n !== 'application').map((n) => <option key={n} value={n}>{n}</option>)}
              </select>
            </label>
            <button type="button" className="text-accent hover:underline" aria-expanded={splitting} onClick={() => setSplitting((s) => !s)}>Split…</button>
          </div>
        )}
        {splitting && (
          <form
            className="flex flex-wrap items-center gap-2 rounded border border-line bg-sunken p-2 text-xs"
            onSubmit={(e) => {
              e.preventDefault();
              if (splitName && splitClasses.size > 0) {
                onEdit({ type: 'SPLIT_MODULE', module: module.name, newName: splitName, classes: [...splitClasses] });
                setSplitting(false);
                setSplitClasses(new Set());
                setSplitName('');
              }
            }}
          >
            <span className="text-muted">Tick the classes below, then name the new module:</span>
            <label className="sr-only" htmlFor={`split-${module.name}`}>New module name</label>
            <input
              id={`split-${module.name}`}
              value={splitName}
              onChange={(e) => setSplitName(e.target.value.toLowerCase())}
              pattern="[a-z][a-z0-9]*"
              maxLength={40}
              placeholder="newmodule"
              className="h-6 w-28 rounded border border-line bg-panel px-1.5 font-mono text-fg"
            />
            <button type="submit" disabled={!splitName || splitClasses.size === 0} className="font-medium text-accent disabled:opacity-40">
              Split {splitClasses.size} class{splitClasses.size === 1 ? '' : 'es'}
            </button>
          </form>
        )}
      </header>
      <ul className="flex-1 divide-y divide-line">
        {module.classes.map((c) => (
          <ClassRow
            key={c.qualifiedName}
            item={c}
            moduleName={module.name}
            targets={others}
            editable={editable}
            onEdit={onEdit}
            showExposure={showExposure && module.category !== 'APPLICATION'}
            splitting={splitting}
            splitChecked={splitClasses.has(c.qualifiedName)}
            onSplitToggle={() =>
              setSplitClasses((current) => {
                const next = new Set(current);
                if (next.has(c.qualifiedName)) next.delete(c.qualifiedName);
                else next.add(c.qualifiedName);
                return next;
              })
            }
          />
        ))}
        {module.classes.length === 0 && <li className="px-4 py-3 text-xs text-faint">No classes — drop one here.</li>}
      </ul>
      {module.evidence.length > 0 && (
        <details className="border-t border-line px-4 py-2 text-xs text-muted">
          <summary className="cursor-pointer text-faint">Why this module</summary>
          <ul className="mt-1 list-disc space-y-0.5 pl-4">{module.evidence.map((e) => <li key={e}>{e}</li>)}</ul>
        </details>
      )}
    </section>
  );
}

function ClassRow({ item, moduleName, targets, editable, onEdit, showExposure, splitting, splitChecked, onSplitToggle }: {
  item: ModuleClass;
  moduleName: string;
  targets: string[];
  editable: boolean;
  onEdit: (edit: ModuleEdit) => void;
  showExposure: boolean;
  splitting: boolean;
  splitChecked: boolean;
  onSplitToggle: () => void;
}) {
  const movable = editable && !item.locked && !item.excluded;
  return (
    <li
      draggable={movable}
      onDragStart={(e) => {
        e.dataTransfer.setData(DRAG_MIME, item.qualifiedName);
        e.dataTransfer.effectAllowed = 'move';
      }}
      className={cn('group flex flex-wrap items-center gap-x-2 gap-y-1 px-3 py-1.5 text-xs', item.excluded && 'opacity-50')}
    >
      {splitting ? (
        <input type="checkbox" checked={splitChecked} onChange={onSplitToggle} aria-label={`Include ${item.className} in the split`} />
      ) : (
        <GripVertical className={cn('h-3.5 w-3.5 shrink-0', movable ? 'cursor-grab text-faint' : 'text-transparent')} aria-hidden />
      )}
      <div className="min-w-0 flex-1 basis-40">
        <p className="flex min-w-0 items-baseline gap-1.5">
          <span className="truncate font-mono text-fg" title={item.qualifiedName}>{item.className}</span>
          <span className="shrink-0 text-faint">{item.componentType.toLowerCase()}</span>
        </p>
        <p className="flex min-w-0 items-center gap-1.5 text-[11px] text-faint">
          {showExposure && item.exposure === 'PUBLIC_API' && <Badge tone="info" title="In the module root package: other modules may use it">API</Badge>}
          {item.origin === 'USER' && <Badge tone="accent">your decision</Badge>}
          {item.locked && <Badge>locked</Badge>}
          {item.excluded && <Badge tone="warn">excluded</Badge>}
          {item.reasons.length > 0 && <span className="truncate" title={item.reasons.join('\n')}>{item.reasons[0]}</span>}
        </p>
      </div>
      <span className="font-mono text-[11px] tabular-nums text-faint" title="Assignment confidence">{Math.round(item.confidence * 100)}%</span>
      {editable && (
        <span className="ml-auto flex shrink-0 items-center gap-1 opacity-70 group-focus-within:opacity-100 group-hover:opacity-100">
          {movable && (
            <select
              aria-label={`Move ${item.className} to module`}
              value=""
              onChange={(e) => {
                const target = e.target.value;
                if (!target) return;
                onEdit(target === 'shared' ? { type: 'MOVE_TO_SHARED', className: item.qualifiedName } : { type: 'MOVE_CLASS', className: item.qualifiedName, target });
              }}
              className="h-6 w-20 rounded border border-line bg-sunken px-1 text-[11px] text-fg"
            >
              <option value="">Move to…</option>
              {targets.filter((t) => t !== 'application').map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          )}
          {showExposure && (
            <select
              aria-label={`Module API visibility of ${item.className}`}
              value={item.exposureOverride ?? ''}
              onChange={(e) => {
                const value = e.target.value;
                onEdit({
                  type: value === 'PUBLIC_API' ? 'EXPOSE_CLASS' : value === 'INTERNAL' ? 'INTERNAL_CLASS' : 'AUTO_EXPOSURE',
                  className: item.qualifiedName,
                });
              }}
              className="h-6 w-20 rounded border border-line bg-sunken px-1 text-[11px] text-fg"
              title="Automatic: public API exactly when another module uses the class"
            >
              <option value="">API: auto</option>
              <option value="PUBLIC_API">Public API</option>
              <option value="INTERNAL">Internal</option>
            </select>
          )}
          {movable && moduleName !== 'shared' && (
            <IconButton label={`Move ${item.className} to shared`} onClick={() => onEdit({ type: 'MOVE_TO_SHARED', className: item.qualifiedName })}>
              <Share2 className="h-3.5 w-3.5" aria-hidden />
            </IconButton>
          )}
          <IconButton
            label={item.excluded ? `Include ${item.className} again` : `Exclude ${item.className} (keep it where it is)`}
            onClick={() => onEdit({ type: item.excluded ? 'INCLUDE_CLASS' : 'EXCLUDE_CLASS', className: item.qualifiedName })}
          >
            {item.excluded ? <Undo2 className="h-3.5 w-3.5" aria-hidden /> : <Ban className="h-3.5 w-3.5" aria-hidden />}
          </IconButton>
          <IconButton
            label={item.locked ? `Unlock ${item.className}` : `Lock ${item.className} in ${moduleName}`}
            onClick={() => onEdit({ type: item.locked ? 'UNLOCK_CLASS' : 'LOCK_CLASS', className: item.qualifiedName })}
          >
            {item.locked ? <LockOpen className="h-3.5 w-3.5" aria-hidden /> : <Lock className="h-3.5 w-3.5" aria-hidden />}
          </IconButton>
        </span>
      )}
    </li>
  );
}

function IconButton({ label, onClick, children }: { label: string; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" aria-label={label} title={label} onClick={onClick} className="rounded p-1 text-muted hover:bg-panel-hover hover:text-fg">
      {children}
    </button>
  );
}
