import { BookOpen, GitFork, Moon, Plus, Sun } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Logo } from './Logo';

export const GITHUB_URL = 'https://github.com/Anvith433/archmorph';
export const DOCS_URL = 'https://github.com/Anvith433/archmorph#readme';

function useTheme(): [string, () => void] {
  const [theme, setTheme] = useState<string>(() => {
    try {
      return localStorage.getItem('archmorph-theme') ?? 'dark';
    } catch {
      return 'dark';
    }
  });
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem('archmorph-theme', theme);
    } catch {
      // storage unavailable: theme stays for this session only
    }
  }, [theme]);
  return [theme, () => setTheme((t) => (t === 'dark' ? 'light' : 'dark'))];
}

export function Topbar({ children }: { children?: React.ReactNode }) {
  const [theme, toggle] = useTheme();
  return (
    <header className="sticky top-0 z-30 flex h-12 items-center gap-4 border-b border-line bg-elevated/95 px-4 backdrop-blur">
      <Link to="/" className="flex shrink-0 items-center gap-2 text-sm font-semibold text-fg" aria-label="ArchMorph home">
        <Logo />
        <span className="hidden sm:inline">ArchMorph</span>
      </Link>
      <div className="min-w-0 flex-1">{children}</div>
      <nav className="flex items-center gap-1" aria-label="Global">
        <Link to="/projects/new" className="hidden h-8 items-center gap-1.5 rounded-md px-2.5 text-sm text-muted hover:bg-panel-hover hover:text-fg sm:inline-flex">
          <Plus className="h-4 w-4" aria-hidden /> New analysis
        </Link>
        <a href={DOCS_URL} target="_blank" rel="noopener noreferrer" className="inline-flex h-8 w-8 items-center justify-center rounded-md text-muted hover:bg-panel-hover hover:text-fg" aria-label="Documentation">
          <BookOpen className="h-4 w-4" aria-hidden />
        </a>
        <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer" className="inline-flex h-8 w-8 items-center justify-center rounded-md text-muted hover:bg-panel-hover hover:text-fg" aria-label="GitHub repository">
          <GitFork className="h-4 w-4" aria-hidden />
        </a>
        <button type="button" onClick={toggle} className="inline-flex h-8 w-8 items-center justify-center rounded-md text-muted hover:bg-panel-hover hover:text-fg" aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}>
          {theme === 'dark' ? <Sun className="h-4 w-4" aria-hidden /> : <Moon className="h-4 w-4" aria-hidden />}
        </button>
      </nav>
    </header>
  );
}
