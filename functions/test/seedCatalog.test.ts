import { describe, expect, it } from 'vitest';

import {
  buildWritePlan,
  chunk,
  MAX_OPS_PER_BATCH,
  seedCatalog,
  type BatchCommitter,
  type FirestoreWrite,
  type SourceCatalog,
} from '../tools/seedCatalog';

/**
 * These tests exercise `seedCatalog`'s chunking/ordering/idempotency CONTRACT against a fake
 * [BatchCommitter] -- no Admin SDK app, no Firestore emulator. `firebase` CLI is not installed in
 * this sandbox (same environment blocker as `test/firestore.rules.test.ts`'s emulator suites), so
 * a real end-to-end run against the emulator is not exercised here. The contract under test --
 * chunk boundaries, taxonomy-before-affirmations, marker strictly last, and "an error mid-run
 * leaves no marker" -- is fully determined by `seedCatalog`'s own code, independent of which
 * `BatchCommitter` implementation executes it, so this coverage is not a placeholder: it is the
 * same assertion the design's emulator-based test would make, minus the real network round-trip.
 */

function sourceCatalog(overrides: Partial<SourceCatalog> = {}): SourceCatalog {
  return {
    catalogVersion: '1.0.0',
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
        access: { tier: 'free', rewardedUnlockHours: null },
        order: 1,
        status: 'active',
      },
    ],
    affirmations: [
      { id: 'u1.t1.c1.001', collectionId: 'u1.t1.c1', text: 'Text 1', order: 1, status: 'active' },
      { id: 'u1.t1.c1.002', collectionId: 'u1.t1.c1', text: 'Text 2', order: 2, status: 'active' },
    ],
    ...overrides,
  };
}

class RecordingCommitter implements BatchCommitter {
  readonly commits: FirestoreWrite[][] = [];
  private readonly failAtCommitIndex: number | null;

  constructor(failAtCommitIndex: number | null = null) {
    this.failAtCommitIndex = failAtCommitIndex;
  }

  async commit(writes: FirestoreWrite[]): Promise<void> {
    if (this.failAtCommitIndex !== null && this.commits.length === this.failAtCommitIndex) {
      throw new Error('simulated mid-run failure');
    }
    this.commits.push(writes);
  }
}

function manyAffirmations(count: number): SourceCatalog['affirmations'] {
  return Array.from({ length: count }, (_, i) => ({
    id: `u1.t1.c1.${String(i + 1).padStart(4, '0')}`,
    collectionId: 'u1.t1.c1',
    text: `Text ${i + 1}`,
    order: i + 1,
    status: 'active',
  }));
}

describe('chunk', () => {
  it('splits items into chunks of at most the given size, preserving order', () => {
    const items = [1, 2, 3, 4, 5, 6, 7];
    const chunks = chunk(items, 3);
    expect(chunks).toEqual([[1, 2, 3], [4, 5, 6], [7]]);
  });

  it('returns zero chunks for an empty array, never one empty chunk', () => {
    expect(chunk([], 10)).toEqual([]);
  });

  it('returns exactly one chunk when items fit within the size', () => {
    expect(chunk([1, 2], 10)).toEqual([[1, 2]]);
  });

  it('a chunk exactly at the boundary size does not spill into an extra empty chunk', () => {
    const items = Array.from({ length: 10 }, (_, i) => i);
    expect(chunk(items, 5)).toEqual([
      [0, 1, 2, 3, 4],
      [5, 6, 7, 8, 9],
    ]);
  });
});

describe('buildWritePlan', () => {
  it('orders taxonomy as universes, then themes, then collections', () => {
    const catalog = sourceCatalog({
      universes: [
        { id: 'u1', title: 'U1', description: 'd', coreNeed: 'c', order: 1, status: 'active' },
        { id: 'u2', title: 'U2', description: 'd', coreNeed: 'c', order: 2, status: 'active' },
      ],
      themes: [
        { id: 'u1.t1', universeId: 'u1', title: 'T1', description: 'd', order: 1, status: 'active' },
      ],
      collections: [
        {
          id: 'u1.t1.c1',
          universeId: 'u1',
          themeId: 'u1.t1',
          access: { tier: 'free', rewardedUnlockHours: null },
          order: 1,
          status: 'active',
        },
      ],
    });
    const { taxonomyWrites } = buildWritePlan(catalog);
    expect(taxonomyWrites.map((w) => w.path)).toEqual([
      'catalogUniverses/u1',
      'catalogUniverses/u2',
      'catalogThemes/u1.t1',
      'catalogCollections/u1.t1.c1',
    ]);
  });

  it('affirmation doc ids are `cat_` + the verbatim source id (design D3)', () => {
    const { affirmationWrites } = buildWritePlan(sourceCatalog());
    expect(affirmationWrites.map((w) => w.path)).toEqual([
      'catalogAffirmations/cat_u1.t1.c1.001',
      'catalogAffirmations/cat_u1.t1.c1.002',
    ]);
  });

  it('the version write path/shape is catalogMeta/version with the source catalogVersion', () => {
    const { versionWrite } = buildWritePlan(sourceCatalog({ catalogVersion: '2.3.4' }));
    expect(versionWrite.path).toBe('catalogMeta/version');
    expect(versionWrite.data.version).toBe('2.3.4');
  });
});

describe('seedCatalog', () => {
  it('chunk boundaries are respected -- no commit exceeds MAX_OPS_PER_BATCH', async () => {
    const catalog = sourceCatalog({ affirmations: manyAffirmations(1000) });
    const committer = new RecordingCommitter();

    await seedCatalog(catalog, committer);

    for (const commit of committer.commits) {
      expect(commit.length).toBeLessThanOrEqual(MAX_OPS_PER_BATCH);
    }
  });

  it('the version marker is the LAST op of the LAST commit -- its own, single-write batch', async () => {
    const catalog = sourceCatalog({ affirmations: manyAffirmations(1000) });
    const committer = new RecordingCommitter();

    await seedCatalog(catalog, committer);

    const lastCommit = committer.commits.at(-1)!;
    expect(lastCommit).toHaveLength(1);
    expect(lastCommit[0].path).toBe('catalogMeta/version');
  });

  it('taxonomy writes are committed before any affirmation write', async () => {
    const catalog = sourceCatalog({ affirmations: manyAffirmations(1000) });
    const committer = new RecordingCommitter();

    await seedCatalog(catalog, committer);

    const allWrites = committer.commits.flat();
    const firstAffirmationIndex = allWrites.findIndex((w) => w.path.startsWith('catalogAffirmations/'));
    const lastTaxonomyIndex = allWrites
      .map((w, i) => (w.path.startsWith('catalogUniverses/') || w.path.startsWith('catalogThemes/') || w.path.startsWith('catalogCollections/') ? i : -1))
      .filter((i) => i !== -1)
      .at(-1)!;
    expect(lastTaxonomyIndex).toBeLessThan(firstAffirmationIndex);
  });

  it('a second run is a no-op-equivalent -- identical writes, safe to re-apply via merge:true', async () => {
    const catalog = sourceCatalog();
    const first = new RecordingCommitter();
    const second = new RecordingCommitter();

    await seedCatalog(catalog, first);
    await seedCatalog(catalog, second);

    expect(second.commits).toEqual(first.commits);
  });

  it('a mid-run abort leaves no marker -- the version write never runs if an earlier commit throws', async () => {
    const catalog = sourceCatalog({ affirmations: manyAffirmations(1000) });
    // Fail on the very first commit -- guarantees the version commit, which is always last, never
    // executes, regardless of how many content chunks preceded it.
    const committer = new RecordingCommitter(0);

    await expect(seedCatalog(catalog, committer)).rejects.toThrow('simulated mid-run failure');
    expect(committer.commits.some((c) => c.some((w) => w.path === 'catalogMeta/version'))).toBe(false);
  });

  it('a failure on the LAST content chunk still leaves no marker committed', async () => {
    const catalog = sourceCatalog({ affirmations: manyAffirmations(1000) });
    const { taxonomyWrites, affirmationWrites } = buildWritePlan(catalog);
    const contentChunkCount = chunk([...taxonomyWrites, ...affirmationWrites], MAX_OPS_PER_BATCH).length;
    const committer = new RecordingCommitter(contentChunkCount - 1);

    await expect(seedCatalog(catalog, committer)).rejects.toThrow('simulated mid-run failure');
    expect(committer.commits).toHaveLength(contentChunkCount - 1);
    expect(committer.commits.flat().some((w) => w.path === 'catalogMeta/version')).toBe(false);
  });
});
