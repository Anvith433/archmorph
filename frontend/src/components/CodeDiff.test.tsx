import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { CodeDiff, collapse, sideBySide } from './CodeDiff';

describe('sideBySide', () => {
  it('pairs replaced lines and numbers both sides', () => {
    const rows = sideBySide('package a;\nclass A {}\n', 'package b;\nclass A {}\n');
    expect(rows[0]).toMatchObject({ kind: 'changed', left: { number: 1, text: 'package a;' }, right: { number: 1, text: 'package b;' } });
    expect(rows[1]).toMatchObject({ kind: 'same', left: { number: 2 }, right: { number: 2 } });
  });

  it('handles pure additions and CRLF input', () => {
    const rows = sideBySide('a\r\nb\r\n', 'a\r\nimport x;\r\nb\r\n');
    expect(rows.map((r) => r.kind)).toEqual(['same', 'added', 'same']);
  });

  it('collapses long unchanged stretches', () => {
    const before = Array.from({ length: 30 }, (_, i) => `line ${i}`).join('\n');
    const after = before.replace('line 15', 'changed');
    const out = collapse(sideBySide(before, after), 2);
    expect(out.filter((r) => 'gap' in r)).toHaveLength(2);
  });
});

describe('CodeDiff', () => {
  it('renders uploaded source strictly as text, never as markup', () => {
    const hostile = 'class A { String s = "<img src=x onerror=alert(1)><script>alert(2)</script>"; }';
    const { container } = render(<CodeDiff before={hostile} after={hostile + '\n// moved'} beforeLabel="A.java" afterLabel="A.java" expanded />);
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('script')).toBeNull();
    expect(screen.getAllByText(/onerror=alert/).length).toBeGreaterThan(0);
  });
});
