import { Link } from 'react-router-dom';
import { Topbar } from '../components/Topbar';
import { EmptyState } from '../components/ui';

export function NotFound({ inline = false }: { inline?: boolean }) {
  const body = (
    <EmptyState title="Page not found">
      <p>
        This address does not exist. <Link className="text-accent hover:underline" to={inline ? '.' : '/'}>Go back</Link>.
      </p>
    </EmptyState>
  );
  if (inline) {
    return body;
  }
  return (
    <div className="min-h-screen">
      <Topbar />
      <main id="main" className="mx-auto max-w-3xl px-4 py-16">{body}</main>
    </div>
  );
}
