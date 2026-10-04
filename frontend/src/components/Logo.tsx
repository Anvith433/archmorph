export function Logo({ className = 'h-6 w-6' }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={className} aria-hidden>
      <rect width="32" height="32" rx="7" className="fill-sunken" />
      <path d="M7 9h8v6H7zM7 17h8v6H7z" fill="none" stroke="currentColor" strokeWidth="2" className="text-faint" />
      <path d="M17 9h8v14h-8z" fill="none" stroke="currentColor" strokeWidth="2" className="text-accent" />
    </svg>
  );
}
