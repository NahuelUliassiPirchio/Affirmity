import { describe, expect, it } from 'vitest';

import { buildCatalog } from '../../tools/catalog/buildCatalog.mjs';

/**
 * RED-first, 3-row source fixture (design D5/D3). `buildCatalog` is a pure
 * `source -> {asset, taxonomyKt}` transform -- no I/O, no bracket gate (that's `bracketGate.mjs`'s
 * job, run separately by `generate-catalog.mjs`'s I/O shell).
 */

function source(overrides = {}) {
  return {
    catalogVersion: '2.0.0',
    universes: [
      { id: 'u1', title: 'U1', description: 'd', coreNeed: 'c', order: 1, status: 'active' },
    ],
    themes: [
      { id: 'u1.t1', universeId: 'u1', title: 'T1', description: 'd', order: 1, status: 'active' },
    ],
    collections: [
      {
        id: 'u1.t1.c1',
        universeId: 'u1',
        themeId: 'u1.t1',
        title: 'C1',
        description: 'd',
        access: { tier: 'free', rewardedUnlockHours: null },
        order: 1,
        status: 'active',
      },
    ],
    affirmations: [
      {
        id: 'u1.t1.c1.001',
        collectionId: 'u1.t1.c1',
        themeId: 'u1.t1',
        universeId: 'u1',
        tone: 'powerful',
        semanticAngle: 'identity',
        title: 'Title 1',
        subtitle: 'Subtitle 1',
        order: 3,
        status: 'active',
      },
      {
        id: 'u1.t1.c1.002',
        collectionId: 'u1.t1.c1',
        themeId: 'u1.t1',
        universeId: 'u1',
        tone: 'powerful',
        semanticAngle: 'identity',
        title: 'Title 2',
        subtitle: 'Subtitle 2',
        order: 1,
        status: 'active',
      },
      {
        id: 'u1.t1.c1.003',
        collectionId: 'u1.t1.c1',
        themeId: 'u1.t1',
        universeId: 'u1',
        tone: 'powerful',
        semanticAngle: 'identity',
        title: 'Title 3',
        subtitle: 'Subtitle 3',
        order: 2,
        status: 'active',
      },
    ],
    ...overrides,
  };
}

describe('buildCatalog', () => {
  it('emits the v2 row shape: id, title, subtitle, groupId, themeId, collectionId, sortOrder', () => {
    const { asset } = buildCatalog(source());
    expect(asset.affirmations[0]).toEqual({
      id: 'cat_u1.t1.c1.002',
      title: 'Title 2',
      subtitle: 'Subtitle 2',
      groupId: 'u1',
      themeId: 'u1.t1',
      collectionId: 'u1.t1.c1',
      sortOrder: 0,
    });
  });

  it('assigns a dense per-group sortOrder ordered by (theme.order, collection.order, affirmation.order)', () => {
    const { asset } = buildCatalog(source());
    const sortOrders = asset.affirmations.map((a) => a.sortOrder);
    expect(sortOrders).toEqual([0, 1, 2]);
    expect(asset.affirmations.map((a) => a.id)).toEqual([
      'cat_u1.t1.c1.002',
      'cat_u1.t1.c1.003',
      'cat_u1.t1.c1.001',
    ]);
  });

  it('fails naming the affirmation id when themeId disagrees with the resolved collection', () => {
    const catalog = source();
    catalog.affirmations[0].themeId = 'wrong.theme';
    expect(() => buildCatalog(catalog)).toThrow(/u1\.t1\.c1\.001/);
  });

  it('fails naming the affirmation id when universeId disagrees with the resolved collection', () => {
    const catalog = source();
    catalog.affirmations[0].universeId = 'wrong-universe';
    expect(() => buildCatalog(catalog)).toThrow(/u1\.t1\.c1\.001/);
  });

  it('fails naming the affirmation id when collectionId is unknown', () => {
    const catalog = source();
    catalog.affirmations[0].collectionId = 'unknown.collection';
    expect(() => buildCatalog(catalog)).toThrow(/u1\.t1\.c1\.001/);
  });

  it('fails on a duplicate affirmation id', () => {
    const catalog = source();
    catalog.affirmations[1].id = catalog.affirmations[0].id;
    expect(() => buildCatalog(catalog)).toThrow(/u1\.t1\.c1\.001/);
  });

  it('returns generated Kotlin taxonomy source containing the catalog version and collection count', () => {
    const { taxonomyKt } = buildCatalog(source());
    expect(taxonomyKt).toContain('2.0.0');
    expect(taxonomyKt).toContain('catalogCollections');
  });
});
