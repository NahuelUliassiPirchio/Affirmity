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
  conceptTagIds: string[];
  desiredStateIds: string[];
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
  title: string;
  description: string;
  access: SourceCollectionAccess;
  conceptTagIds: string[];
  contextIds: string[];
  momentIds: string[];
  desiredStateIds: string[];
  order: number;
  status: string;
}

/** v2 copy shape: `title` + `subtitle`, no `text`, no `legacyText` (spec "v2 Copy Shape"). */
export interface SourceAffirmation {
  id: string;
  collectionId: string;
  themeId: string;
  universeId: string;
  tone: string;
  semanticAngle: string;
  title: string;
  subtitle: string;
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
      conceptTagIds: t.conceptTagIds,
      desiredStateIds: t.desiredStateIds,
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
      title: c.title,
      description: c.description,
      access: {
        tier: c.access.tier,
        rewardedUnlockHours: c.access.rewardedUnlockHours,
      },
      conceptTagIds: c.conceptTagIds,
      contextIds: c.contextIds,
      momentIds: c.momentIds,
      desiredStateIds: c.desiredStateIds,
      order: c.order,
      status: c.status,
    },
  };
}

/** v2 copy shape (spec "v2 Copy Shape Across the Pipeline"): `title`+`subtitle`+`tone`+
 *  `semanticAngle`, never `text`/`legacyText`. `groupId`/`themeId` come from the affirmation's own
 *  validated `universeId`/`themeId` fields (already cross-checked against the resolved collection
 *  by [parseSourceCatalog]), not derived from splitting `collectionId`. */
function affirmationWrite(a: SourceAffirmation, catalogVersion: string): FirestoreWrite {
  // Design D3: catalog id = `cat_` + the source dotted id, verbatim. Same scheme Room uses, so an
  // id maps 1:1 between the local cache and the shared Firestore document.
  return {
    path: `catalogAffirmations/${CATALOG_ID_PREFIX}${a.id}`,
    data: {
      title: a.title,
      subtitle: a.subtitle,
      tone: a.tone,
      semanticAngle: a.semanticAngle,
      groupId: a.universeId,
      themeId: a.themeId,
      collectionId: a.collectionId,
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

// --- Runtime shape validation (D2) ---------------------------------------------------------
//
// Hand-rolled instead of a schema library (zod is absent from functions/package.json -- adding
// it for a dev-only script would put a runtime dependency in the deployed Functions bundle).
// Every helper throws an `Error` naming the offending document id, mirroring
// `CatalogAssetParser.kt`'s `require(...) { "$id ..." }` convention.

function requireString(value: unknown, id: string, field: string): string {
  if (typeof value !== 'string') {
    throw new Error(`${id}: expected string field "${field}", got ${typeof value}`);
  }
  return value;
}

function requireNonEmptyString(value: unknown, id: string, field: string): string {
  const s = requireString(value, id, field);
  if (s.length === 0) throw new Error(`${id}: field "${field}" must not be empty`);
  return s;
}

function requireInt(value: unknown, id: string, field: string): number {
  if (typeof value !== 'number' || !Number.isInteger(value)) {
    throw new Error(`${id}: expected integer field "${field}", got ${typeof value}`);
  }
  return value;
}

function requireStringArray(value: unknown, id: string, field: string): string[] {
  if (!Array.isArray(value) || !value.every((v) => typeof v === 'string')) {
    throw new Error(`${id}: expected string[] field "${field}"`);
  }
  return value;
}

function requireEnum<T extends string>(value: unknown, id: string, field: string, allowed: readonly T[]): T {
  const s = requireString(value, id, field);
  if (!(allowed as readonly string[]).includes(s)) {
    throw new Error(`${id}: field "${field}" must be one of ${allowed.join('|')}, got "${s}"`);
  }
  return s as T;
}

function parseUniverse(raw: unknown): SourceUniverse {
  const u = raw as Record<string, unknown>;
  const id = requireNonEmptyString(u.id, '<universe>', 'id');
  return {
    id,
    title: requireNonEmptyString(u.title, id, 'title'),
    description: requireString(u.description, id, 'description'),
    coreNeed: requireString(u.coreNeed, id, 'coreNeed'),
    order: requireInt(u.order, id, 'order'),
    status: requireString(u.status, id, 'status'),
  };
}

function parseTheme(raw: unknown): SourceTheme {
  const t = raw as Record<string, unknown>;
  const id = requireNonEmptyString(t.id, '<theme>', 'id');
  return {
    id,
    universeId: requireNonEmptyString(t.universeId, id, 'universeId'),
    title: requireNonEmptyString(t.title, id, 'title'),
    description: requireString(t.description, id, 'description'),
    conceptTagIds: requireStringArray(t.conceptTagIds, id, 'conceptTagIds'),
    desiredStateIds: requireStringArray(t.desiredStateIds, id, 'desiredStateIds'),
    order: requireInt(t.order, id, 'order'),
    status: requireString(t.status, id, 'status'),
  };
}

function parseCollection(raw: unknown): SourceCollection {
  const c = raw as Record<string, unknown>;
  const id = requireNonEmptyString(c.id, '<collection>', 'id');
  const accessRaw = c.access as Record<string, unknown> | undefined;
  if (typeof accessRaw !== 'object' || accessRaw === null) {
    throw new Error(`${id}: missing "access" object`);
  }
  const tier = requireEnum(accessRaw.tier, id, 'access.tier', ['free', 'pro'] as const);
  const rewardedUnlockHours =
    accessRaw.rewardedUnlockHours === null ? null : requireInt(accessRaw.rewardedUnlockHours, id, 'access.rewardedUnlockHours');
  if (tier === 'free' && rewardedUnlockHours !== null) {
    throw new Error(`${id}: declares tier=free with non-null rewardedUnlockHours`);
  }
  if (rewardedUnlockHours !== null && rewardedUnlockHours <= 0) {
    throw new Error(`${id}: declares non-positive rewardedUnlockHours`);
  }
  return {
    id,
    universeId: requireNonEmptyString(c.universeId, id, 'universeId'),
    themeId: requireNonEmptyString(c.themeId, id, 'themeId'),
    title: requireNonEmptyString(c.title, id, 'title'),
    description: requireString(c.description, id, 'description'),
    access: { tier, rewardedUnlockHours },
    conceptTagIds: requireStringArray(c.conceptTagIds, id, 'conceptTagIds'),
    contextIds: requireStringArray(c.contextIds, id, 'contextIds'),
    momentIds: requireStringArray(c.momentIds, id, 'momentIds'),
    desiredStateIds: requireStringArray(c.desiredStateIds, id, 'desiredStateIds'),
    order: requireInt(c.order, id, 'order'),
    status: requireString(c.status, id, 'status'),
  };
}

function parseAffirmation(raw: unknown): SourceAffirmation {
  const a = raw as Record<string, unknown>;
  const id = requireNonEmptyString(a.id, '<affirmation>', 'id');
  return {
    id,
    collectionId: requireNonEmptyString(a.collectionId, id, 'collectionId'),
    themeId: requireNonEmptyString(a.themeId, id, 'themeId'),
    universeId: requireNonEmptyString(a.universeId, id, 'universeId'),
    tone: requireString(a.tone, id, 'tone'),
    semanticAngle: requireString(a.semanticAngle, id, 'semanticAngle'),
    title: requireNonEmptyString(a.title, id, 'title'),
    subtitle: requireNonEmptyString(a.subtitle, id, 'subtitle'),
    order: requireInt(a.order, id, 'order'),
    status: requireString(a.status, id, 'status'),
  };
}

/**
 * Runtime parse boundary for the v2 source catalog (design D2). Validates every field's presence
 * and type, the free/rewardedUnlockHours invariant, affirmation id uniqueness, that every
 * `affirmation.collectionId` resolves to a known collection, and that the affirmation's own
 * (denormalized) `themeId`/`universeId` agree with the resolved collection's -- throwing an
 * `Error` naming the offending id on the FIRST violation found. Never silently writes `undefined`.
 */
export function parseSourceCatalog(raw: unknown): SourceCatalog {
  const root = raw as Record<string, unknown>;
  const catalogVersion = requireNonEmptyString(root.catalogVersion, '<catalog>', 'catalogVersion');

  if (!Array.isArray(root.universes)) throw new Error('<catalog>: expected array field "universes"');
  if (!Array.isArray(root.themes)) throw new Error('<catalog>: expected array field "themes"');
  if (!Array.isArray(root.collections)) throw new Error('<catalog>: expected array field "collections"');
  if (!Array.isArray(root.affirmations)) throw new Error('<catalog>: expected array field "affirmations"');

  const universes = root.universes.map(parseUniverse);
  const themes = root.themes.map(parseTheme);
  const collections = root.collections.map(parseCollection);
  const affirmations = root.affirmations.map(parseAffirmation);

  const collectionById = new Map(collections.map((c) => [c.id, c]));
  const seenAffirmationIds = new Set<string>();
  for (const a of affirmations) {
    if (seenAffirmationIds.has(a.id)) {
      throw new Error(`${a.id}: duplicate affirmation id`);
    }
    seenAffirmationIds.add(a.id);

    const collection = collectionById.get(a.collectionId);
    if (!collection) {
      throw new Error(`${a.id}: references unknown collectionId ${a.collectionId}`);
    }
    if (a.themeId !== collection.themeId) {
      throw new Error(`${a.id}: themeId "${a.themeId}" disagrees with resolved collection "${collection.themeId}"`);
    }
    if (a.universeId !== collection.universeId) {
      throw new Error(`${a.id}: universeId "${a.universeId}" disagrees with resolved collection "${collection.universeId}"`);
    }
  }

  return { catalogVersion, universes, themes, collections, affirmations };
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
  const catalog = parseSourceCatalog(JSON.parse(readFileSync(catalogPath, 'utf8')));

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
