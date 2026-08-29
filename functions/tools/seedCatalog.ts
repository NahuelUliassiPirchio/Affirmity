/**
 * One-time, admin-privileged publisher for the curated affirmation catalog (design D12).
 *
 * Deliberately a TypeScript script in `functions/tools/`, NOT a Kotlin `CatalogSeedPlan.kt`:
 * `firestore.rules` denies all client writes to the catalog collections, so the app can never run
 * this; `functions/` already ships `firebase-admin`, `typescript`, and `vitest`. Run once by a
 * developer, via the Admin SDK, which bypasses rules entirely.
 *
 * Usage (from `functions/`):
 *   npx tsx tools/seedCatalog.ts --catalog /path/to/affirmations-catalog.v1.json
 *
 * The `--catalog` argument MUST be the FULL source JSON (the same file passed to
 * `tools/catalog/generate-catalog.mjs`, shape `{ catalogVersion, universes, themes, collections,
 * affirmations }`) -- NOT the bundled `app/src/main/assets/catalog.v1.json`, which is a trimmed
 * `{ version, affirmations }` asset with no taxonomy/access data (measured, see
 * `data/catalog/CatalogAssetParser.kt`). Only the full source carries what `catalogUniverses`/
 * `catalogThemes`/`catalogCollections` need. Authentication is via `GOOGLE_APPLICATION_CREDENTIALS`
 * (standard Admin SDK application-default-credentials resolution) -- no credentials are read or
 * embedded here.
 *
 * Discipline mirrored from `MigrationPlan.chunkWithMarkerLast` (Kotlin, app-side): chunk writes so
 * no single commit exceeds Firestore's 500-write batch limit, publish taxonomy before affirmations,
 * and write the `catalogMeta/version` marker STRICTLY LAST -- its presence/value is the "seeded
 * through" signal a client's delta-fetch reads (design D2). Idempotent: every write is
 * `set(..., { merge: true })`, so a re-run (including a re-run after a partial failure) is safe.
 */

export const CATALOG_ID_PREFIX = 'cat_';

/** Firestore write budget per batch. Firestore's hard limit is 500 operations; 450 leaves
 *  headroom rather than shipping exactly at the ceiling. */
export const MAX_OPS_PER_BATCH = 450;

export interface SourceUniverse {
  id: string;
  title: string;
  description: string;
  coreNeed: string;
  order: number;
  status: string;
}

export interface SourceTheme {
  id: string;
  universeId: string;
  title: string;
  description: string;
  order: number;
  status: string;
}

export interface SourceCollectionAccess {
  tier: 'free' | 'pro';
  rewardedUnlockHours: number | null;
}

export interface SourceCollection {
  id: string;
  universeId: string;
  themeId: string;
  access: SourceCollectionAccess;
  order: number;
  status: string;
}

export interface SourceAffirmation {
  id: string;
  collectionId: string;
  text: string;
  order: number;
  status: string;
}

export interface SourceCatalog {
  catalogVersion: string;
  universes: SourceUniverse[];
  themes: SourceTheme[];
  collections: SourceCollection[];
  affirmations: SourceAffirmation[];
}

/** A single Firestore document write -- `path` is a full doc path (`collection/docId`), `data` is
 *  whatever `set(..., { merge: true })` will write. Committer-agnostic so the chunking/ordering
 *  logic is testable with a fake, without an Admin SDK app or a running emulator. */
export interface FirestoreWrite {
  path: string;
  data: Record<string, unknown>;
}

export interface BatchCommitter {
  /** Commits exactly one batch (<= [MAX_OPS_PER_BATCH] writes). Never called with an empty array. */
  commit(writes: FirestoreWrite[]): Promise<void>;
}

/** Splits [items] into chunks of at most [size], preserving order. The LAST chunk may be smaller
 *  than [size]; an empty [items] yields zero chunks (never one empty chunk), so a caller never
 *  commits a no-op batch. */
export function chunk<T>(items: readonly T[], size: number): T[][] {
  if (size <= 0) throw new Error(`chunk size must be positive, got ${size}`);
  const chunks: T[][] = [];
  for (let i = 0; i < items.length; i += size) {
    chunks.push(items.slice(i, i + size));
  }
  return chunks;
}

function universeWrite(u: SourceUniverse): FirestoreWrite {
  return {
    path: `catalogUniverses/${u.id}`,
    data: {
      title: u.title,
      description: u.description,
      coreNeed: u.coreNeed,
      order: u.order,
      status: u.status,
    },
  };
}

function themeWrite(t: SourceTheme): FirestoreWrite {
  return {
    path: `catalogThemes/${t.id}`,
    data: {
      universeId: t.universeId,
      title: t.title,
      description: t.description,
      order: t.order,
      status: t.status,
    },
  };
}

function collectionWrite(c: SourceCollection): FirestoreWrite {
  return {
    path: `catalogCollections/${c.id}`,
    data: {
      universeId: c.universeId,
      themeId: c.themeId,
      access: {
        tier: c.access.tier,
        rewardedUnlockHours: c.access.rewardedUnlockHours,
      },
      order: c.order,
      status: c.status,
    },
  };
}

function affirmationWrite(a: SourceAffirmation, catalogVersion: string): FirestoreWrite {
  const collection = a.collectionId;
  // Design D3: catalog id = `cat_` + the source dotted id, verbatim. Same scheme Room uses, so an
  // id maps 1:1 between the local cache and the shared Firestore document.
  return {
    path: `catalogAffirmations/${CATALOG_ID_PREFIX}${a.id}`,
    data: {
      text: a.text,
      groupId: a.collectionId.split('.')[0],
      themeId: a.collectionId.split('.').slice(0, 2).join('.'),
      collectionId: collection,
      sortOrder: a.order,
      status: a.status,
      catalogVersion,
    },
  };
}

/** Ordered write plan: taxonomy (universes, then themes, then collections) BEFORE affirmations,
 *  `catalogMeta/version` returned SEPARATELY so the caller can commit it strictly last (D12/D13). */
export function buildWritePlan(catalog: SourceCatalog): {
  taxonomyWrites: FirestoreWrite[];
  affirmationWrites: FirestoreWrite[];
  versionWrite: FirestoreWrite;
} {
  const taxonomyWrites = [
    ...catalog.universes.map(universeWrite),
    ...catalog.themes.map(themeWrite),
    ...catalog.collections.map(collectionWrite),
  ];
  const affirmationWrites = catalog.affirmations.map((a) => affirmationWrite(a, catalog.catalogVersion));
  const versionWrite: FirestoreWrite = {
    path: 'catalogMeta/version',
    data: { version: catalog.catalogVersion, seededAtMillis: Date.now() },
  };
  return { taxonomyWrites, affirmationWrites, versionWrite };
}

/**
 * Publishes [catalog] via [committer]: taxonomy first, affirmations next, each chunked at
 * [MAX_OPS_PER_BATCH], then `catalogMeta/version` as the LAST commit -- a single-write batch,
 * always last, regardless of how the preceding writes chunked. A thrown error from any chunk
 * (including the version commit) propagates and leaves no marker written, by construction: the
 * marker is the very last statement to run.
 */
export async function seedCatalog(catalog: SourceCatalog, committer: BatchCommitter): Promise<void> {
  const { taxonomyWrites, affirmationWrites, versionWrite } = buildWritePlan(catalog);
  const contentWrites = [...taxonomyWrites, ...affirmationWrites];
  for (const batch of chunk(contentWrites, MAX_OPS_PER_BATCH)) {
    await committer.commit(batch);
  }
  await committer.commit([versionWrite]);
}

function parseArgs(argv: string[]): { catalogPath: string } {
  const flagIndex = argv.indexOf('--catalog');
  if (flagIndex === -1 || flagIndex === argv.length - 1) {
    throw new Error('Usage: tsx tools/seedCatalog.ts --catalog /path/to/affirmations-catalog.v1.json');
  }
  return { catalogPath: argv[flagIndex + 1] };
}

async function main(): Promise<void> {
  // Deferred requires: keep these out of the module's static import graph so vitest can import
  // the pure functions above (chunk/buildWritePlan/seedCatalog) without needing an Admin SDK app
  // or file-system access -- this function only runs when the script is invoked directly.
  const { readFileSync } = await import('node:fs');
  const { initializeApp } = await import('firebase-admin/app');
  const { getFirestore } = await import('firebase-admin/firestore');

  const { catalogPath } = parseArgs(process.argv.slice(2));
  const catalog = JSON.parse(readFileSync(catalogPath, 'utf8')) as SourceCatalog;

  initializeApp();
  const db = getFirestore();

  const committer: BatchCommitter = {
    async commit(writes) {
      const batch = db.batch();
      for (const write of writes) {
        batch.set(db.doc(write.path), write.data, { merge: true });
      }
      await batch.commit();
    },
  };

  await seedCatalog(catalog, committer);
  console.log(
    `[seedCatalog] done. version=${catalog.catalogVersion} universes=${catalog.universes.length} ` +
      `themes=${catalog.themes.length} collections=${catalog.collections.length} ` +
      `affirmations=${catalog.affirmations.length}`,
  );
}

if (require.main === module) {
  main().catch((error) => {
    console.error('[seedCatalog] FAILED:', error);
    process.exitCode = 1;
  });
}
