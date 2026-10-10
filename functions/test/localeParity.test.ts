import { describe, expect, it } from 'vitest';

import { assertLocaleParity } from '../../tools/catalog/localeParity.mjs';
import { generateAllLocales } from '../../tools/catalog/generate-catalog.mjs';

type Aff = Record<string, unknown>;

function aff(id: string, over: Aff = {}): Aff {
  return {
    id,
    collectionId: 'u1.t1.c1',
    themeId: 'u1.t1',
    universeId: 'u1',
    title: `Title ${id}`,
    subtitle: `Subtitle ${id}`,
    order: 0,
    tone: 'direct',
    semanticAngle: 'action',
    ...over,
  };
}

function source(affirmations: Aff[], catalogVersion = '5.1.0') {
  return {
    catalogVersion,
    universes: [{ id: 'u1', title: 'U1', description: 'd', coreNeed: 'c' }],
    themes: [{ id: 'u1.t1', title: 'T1', description: 'd', universeId: 'u1' }],
    collections: [{ id: 'u1.t1.c1', title: 'C1', description: 'd', themeId: 'u1.t1', universeId: 'u1' }],
    affirmations: affirmations.map((a, order) => ({ ...a, order })),
  };
}

describe('assertLocaleParity', () => {
  it('passes for matching ids, order and metadata', () => {
    const base = source([aff('a'), aff('b')]);
    const other = source([aff('a', { title: 'Otro a' }), aff('b', { title: 'Otro b' })]);
    expect(assertLocaleParity(base, other)).toEqual({ warnings: [] });
  });

  it('fails naming a missing id', () => {
    const base = source([aff('a'), aff('b')]);
    const other = source([aff('a')]);
    expect(() => assertLocaleParity(base, other)).toThrow(/missing.*\bb\b/is);
  });

  it('fails naming an extra id', () => {
    const base = source([aff('a')]);
    const other = source([aff('a'), aff('zzz')]);
    expect(() => assertLocaleParity(base, other)).toThrow(/extra.*zzz/is);
  });

  it('fails when the order drifts', () => {
    const base = source([aff('a'), aff('b')]);
    const other = source([aff('b'), aff('a')]);
    expect(() => assertLocaleParity(base, other)).toThrow(/order/i);
  });

  it('fails naming the id and field when metadata differs', () => {
    const base = source([aff('a'), aff('b')]);
    const other = source([aff('a'), aff('b', { tone: 'powerful' })]);
    expect(() => assertLocaleParity(base, other)).toThrow(/b.*tone/is);
  });

  it('fails when the catalog version differs', () => {
    const base = source([aff('a')]);
    const other = source([aff('a')], '5.0.0');
    expect(() => assertLocaleParity(base, other)).toThrow(/catalogVersion/);
  });

  it('fails naming id, field and both counts when token counts differ', () => {
    const base = source([aff('a', { title: 'Hola' })]);
    const other = source([aff('a', { title: 'Hello [Ana]' })]);
    expect(() => assertLocaleParity(base, other)).toThrow(/a.*title.*0.*1/is);
  });

  it('checks the subtitle field too', () => {
    const base = source([aff('a', { subtitle: 'Hola [x]' })]);
    const other = source([aff('a', { subtitle: 'Hello' })]);
    expect(() => assertLocaleParity(base, other)).toThrow(/a.*subtitle.*1.*0/is);
  });

  it('does not count an empty [] as a token', () => {
    const base = source([aff('a', { title: 'Hola []' })]);
    const other = source([aff('a', { title: 'Hello' })]);
    expect(() => assertLocaleParity(base, other)).not.toThrow();
  });

  it('returns a warning, not a failure, for a field with more than one token', () => {
    const base = source([aff('a', { title: '[x] y [z]' })]);
    const other = source([aff('a', { title: '[p] q [r]' })]);
    const { warnings } = assertLocaleParity(base, other);
    expect(warnings).toHaveLength(1);
    expect(warnings[0]).toMatch(/a.*title.*2/s);
  });
});

describe('generateAllLocales', () => {
  const taxonomy = {
    universes: [{ id: 'u1', title: 'U1', description: 'd', coreNeed: 'c', order: 1, status: 'active' }],
    themes: [{ id: 'u1.t1', title: 'T1', description: 'd', universeId: 'u1', order: 1 }],
    collections: [
      {
        id: 'u1.t1.c1',
        title: 'C1',
        description: 'd',
        themeId: 'u1.t1',
        universeId: 'u1',
        order: 1,
        access: { tier: 'free', rewardedUnlockHours: null },
      },
    ],
  };
  const header = 'group,theme,collection,id,sortOrder,tone,semanticAngle,title,subtitle\n';
  const row = (id: string, title: string, subtitle: string) =>
    `u1,u1.t1,u1.t1.c1,cat_${id},1,direct,action,${title},${subtitle}\n`;

  it('returns an asset per locale and the taxonomy from es only', () => {
    const out = generateAllLocales(
      {
        es: header + row('u1.t1.c1.001', 'Hola', 'Mundo'),
        en: header + row('u1.t1.c1.001', 'Hello', 'World'),
      },
      taxonomy,
    );
    expect(Object.keys(out.assets).sort()).toEqual(['en', 'es']);
    expect(out.assets.es.assetJson).toContain('"title":"Hola"');
    expect(out.assets.en.assetJson).toContain('"title":"Hello"');
    expect(typeof out.taxonomyKt).toBe('string');
    expect(out.warnings).toEqual([]);
  });

  it('fails on parity drift before returning anything to write', () => {
    expect(() =>
      generateAllLocales(
        {
          es: header + row('u1.t1.c1.001', 'Hola [Ana]', 'Mundo'),
          en: header + row('u1.t1.c1.001', 'Hello', 'World'),
        },
        taxonomy,
      ),
    ).toThrow(/001.*title/is);
  });
});
