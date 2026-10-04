import { ArrowDown, ArrowRight, Boxes, FileDiff, GitBranch, GitFork, Network, ShieldCheck, UploadCloud } from 'lucide-react';
import { Link } from 'react-router-dom';
import { DOCS_URL, GITHUB_URL, Topbar } from '../components/Topbar';

const FEATURES = [
  { icon: Network, title: 'Semantic dependency graph', body: 'JavaParser AST, import-aware type resolution and twelve dependency kinds — fields, generics, calls, inheritance, entity relationships.' },
  { icon: Boxes, title: 'Business module discovery', body: 'A documented affinity model combines dependencies, naming, packages, entities, endpoints and type usage. Shared code stays shared.' },
  { icon: GitBranch, title: 'You stay in control', body: 'Rename, merge, split, move, lock or exclude. ArchMorph shows its suggestion, your decisions and the final plan side by side.' },
  { icon: FileDiff, title: 'Reviewable rewrites', body: 'Packages, imports and qualified names are rewritten from AST positions. Inspect every diff before you download anything.' },
  { icon: ShieldCheck, title: 'Validated output', body: 'Seven validation levels — from file system and parsing to a sandboxed, allowlisted Maven build.' },
];

function Stack({ title, rows, tone }: { title: string; rows: string[]; tone: 'muted' | 'accent' }) {
  return (
    <div className="w-full rounded-lg border border-line bg-panel p-4 sm:w-64">
      <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-faint">{title}</p>
      <div className="space-y-1.5">
        {rows.map((row) => (
          <div key={row} className={`rounded border px-3 py-1.5 font-mono text-xs ${tone === 'accent' ? 'border-accent/40 bg-accent-soft text-fg' : 'border-line bg-sunken text-muted'}`}>
            {row}
          </div>
        ))}
      </div>
    </div>
  );
}

export function Landing() {
  return (
    <div className="min-h-screen">
      <Topbar />
      <main id="main">
        <section className="mx-auto max-w-6xl px-6 pb-16 pt-20">
          <p className="mb-4 inline-flex items-center gap-2 rounded-full border border-line bg-panel px-3 py-1 text-xs text-muted">
            Open source · static analysis with human review
          </p>
          <h1 className="max-w-3xl text-4xl font-semibold tracking-tight text-fg sm:text-5xl">
            Understand your architecture.
            <br />
            <span className="text-muted">Discover business modules.</span>
          </h1>
          <p className="mt-5 max-w-2xl text-lg text-muted">
            Transform layered Java applications into modular monoliths — with a deterministic, explainable plan you review before a single file changes.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to="/projects/new" className="inline-flex h-10 items-center gap-2 rounded-md bg-accent-strong px-4 text-sm font-medium text-white hover:bg-accent">
              <UploadCloud className="h-4 w-4" aria-hidden /> Analyze a project
            </Link>
            <a href={DOCS_URL} target="_blank" rel="noopener noreferrer" className="inline-flex h-10 items-center gap-2 rounded-md border border-line-strong bg-panel px-4 text-sm text-fg hover:bg-panel-hover">
              Read the docs
            </a>
            <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className="inline-flex h-10 items-center gap-2 rounded-md px-4 text-sm text-muted hover:text-fg">
              <GitFork className="h-4 w-4" aria-hidden /> GitHub
            </a>
          </div>
        </section>

        <section aria-label="How it works" className="border-y border-line bg-elevated">
          <div className="mx-auto flex max-w-6xl flex-col items-center gap-4 px-6 py-12 lg:flex-row lg:justify-between">
            <Stack title="Layered architecture" tone="muted" rows={['controller/', 'service/', 'repository/', 'entity/', 'dto/']} />
            <div className="flex flex-col items-center gap-2 text-center text-xs text-muted">
              <ArrowRight className="hidden h-5 w-5 text-accent lg:block" aria-hidden />
              <ArrowDown className="h-5 w-5 text-accent lg:hidden" aria-hidden />
              <div className="rounded-lg border border-accent/40 bg-accent-soft px-4 py-3 font-medium text-fg">ArchMorph analysis</div>
              <span className="max-w-[12rem]">parse · graph · discover · plan · rewrite · validate</span>
              <ArrowRight className="hidden h-5 w-5 text-accent lg:block" aria-hidden />
              <ArrowDown className="h-5 w-5 text-accent lg:hidden" aria-hidden />
            </div>
            <Stack title="Modular monolith" tone="accent" rows={['modules/user/', 'modules/order/', 'modules/payment/', 'shared/security/', 'shared/common/']} />
          </div>
        </section>

        <section className="mx-auto grid max-w-6xl gap-4 px-6 py-16 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map(({ icon: Icon, title, body }) => (
            <article key={title} className="rounded-lg border border-line bg-panel p-5">
              <Icon className="h-5 w-5 text-accent" aria-hidden />
              <h2 className="mt-3 text-sm font-semibold text-fg">{title}</h2>
              <p className="mt-1.5 text-sm text-muted">{body}</p>
            </article>
          ))}
          <article className="rounded-lg border border-warn/30 bg-warn-soft p-5">
            <h2 className="text-sm font-semibold text-fg">Honest limits</h2>
            <p className="mt-1.5 text-sm text-muted">
              ArchMorph does not promise behavioural equivalence. Reflection, string-based class references, generated code and explicit package
              scanning are flagged for manual review instead of being changed silently.
            </p>
          </article>
        </section>
      </main>
    </div>
  );
}
