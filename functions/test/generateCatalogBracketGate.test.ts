import { describe, expect, it } from 'vitest';

import { runBracketGate } from '../../tools/catalog/generate-catalog.mjs';

/**
 * Fix 6: `runBracketGate` used to assume every text field (including `title`) was always a string
 * -- `if (typeof value !== "string") continue;` skipped the bracket regex entirely for a non-string
 * `title`, letting a malformed source entry through generation and crash later, at runtime, on
 * user devices (`CatalogAssetParser.kt`'s non-null `title` read). This must fail loudly at
 * generation time instead.
 */

function source(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    universes: [
      { id: 'u1', title: 'U1', description: 'd', coreNeed: 'c' },
    ],
    themes: [
      { id: 'u1.t1', title: 'T1', description: 'd' },
    ],
    collections: [
      { id: 'u1.t1.c1', title: 'C1', description: 'd' },
    ],
    affirmations: [
      { id: 'u1.t1.c1.001', title: 'Title 1', subtitle: 'Subtitle 1' },
    ],
    ...overrides,
  };
}

describe('runBracketGate (Fix 6: non-string title must fail loudly, not skip validation)', () => {
  it('accepts a well-formed source with clean string titles', () => {
    expect(() => runBracketGate(source())).not.toThrow();
  });

  it('still fails on an illegal bracket in a valid string title', () => {
    const bad = source({ affirmations: [{ id: 'a1', title: 'You are ] broken', subtitle: undefined }] });
    expect(() => runBracketGate(bad)).toThrow(/illegal bracket/);
  });

  it('throws instead of silently skipping when affirmation.title is not a string', () => {
    const bad = source({ affirmations: [{ id: 'a1', title: 42, subtitle: undefined }] });
    expect(() => runBracketGate(bad)).toThrow(/title/i);
  });

  it('throws when affirmation.title is null', () => {
    const bad = source({ affirmations: [{ id: 'a1', title: null, subtitle: undefined }] });
    expect(() => runBracketGate(bad)).toThrow(/title/i);
  });

  it('throws when universe.title is not a string', () => {
    const bad = source({ universes: [{ id: 'u1', title: [], description: 'd', coreNeed: 'c' }] });
    expect(() => runBracketGate(bad)).toThrow(/title/i);
  });

  it('throws when theme.title is not a string', () => {
    const bad = source({ themes: [{ id: 'u1.t1', title: {}, description: 'd' }] });
    expect(() => runBracketGate(bad)).toThrow(/title/i);
  });

  it('throws when collection.title is not a string', () => {
    const bad = source({ collections: [{ id: 'u1.t1.c1', title: 7, description: 'd' }] });
    expect(() => runBracketGate(bad)).toThrow(/title/i);
  });

  it('throws when affirmation.title is an empty string', () => {
    const bad = source({ affirmations: [{ id: 'a1', title: '', subtitle: undefined }] });
    expect(() => runBracketGate(bad)).toThrow(/title/i);
  });

  it('still allows an absent (optional) subtitle without throwing', () => {
    const ok = source({ affirmations: [{ id: 'a1', title: 'Fine', subtitle: undefined }] });
    expect(() => runBracketGate(ok)).not.toThrow();
  });
});
