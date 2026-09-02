/**
 * One-time, admin-privileged publisher for the `notificationCopy` collection (design §1's seed
 * script paragraph). Mirrors `functions/tools/seedCatalog.ts`'s shape verbatim: a TypeScript
 * script in `functions/tools/`, NOT client code -- `firestore.rules` denies ALL client read/write
 * on `notificationCopy`, so only the Admin SDK (which bypasses rules) can ever populate it.
 *
 * Usage (from `functions/`):
 *   npx tsx tools/seedCopyCatalog.ts --catalog tools/notification-copy.v1.json
 *
 * Idempotent: every write is `set(..., { merge: true })`, batched at `MAX_OPS_PER_BATCH` (450,
 * same headroom-under-500 rationale as `seedCatalog.ts`), so a re-run (including after a partial
 * failure) is safe. Console edits to `notificationCopy` thereafter are expected and untouched by
 * a re-run, as long as the corresponding key/fields in this JSON stay stable.
 */

import type { CopyLocale, CopyVariant, NotificationFamily } from '../src/copyCatalog';

export const MAX_OPS_PER_BATCH = 450;

export interface CopyCatalogFile {
  version: string;
  variants: CopyVariant[];
}

export interface CopyFirestoreWrite {
  path: string;
  data: Record<string, unknown>;
}

export interface CopyCommitter {
  /** Commits exactly one batch (<= [MAX_OPS_PER_BATCH] writes). Never called with an empty array. */
  commit(writes: CopyFirestoreWrite[]): Promise<void>;
}

function copyVariantWrite(variant: CopyVariant): CopyFirestoreWrite {
  return {
    path: `notificationCopy/${variant.key}`,
    data: {
      family: variant.family,
      context: variant.context,
      placeholders: variant.placeholders,
      enabled: variant.enabled,
      order: variant.order,
      locales: variant.locales,
    },
  };
}

/** One write per variant, doc id === `variant.key`, path `notificationCopy/{key}`. */
export function buildCopyWritePlan(catalog: CopyCatalogFile): CopyFirestoreWrite[] {
  return catalog.variants.map(copyVariantWrite);
}

/** Splits [writes] into chunks of at most [MAX_OPS_PER_BATCH], preserving order. Never produces an
 *  empty chunk for an empty input. */
export function chunkCopyWrites(writes: CopyFirestoreWrite[]): CopyFirestoreWrite[][] {
  const chunks: CopyFirestoreWrite[][] = [];
  for (let i = 0; i < writes.length; i += MAX_OPS_PER_BATCH) {
    chunks.push(writes.slice(i, i + MAX_OPS_PER_BATCH));
  }
  return chunks;
}

/** Publishes every variant in [catalog] via [committer], chunked at [MAX_OPS_PER_BATCH]. */
export async function seedCopyCatalog(catalog: CopyCatalogFile, committer: CopyCommitter): Promise<void> {
  const writes = buildCopyWritePlan(catalog);
  for (const batch of chunkCopyWrites(writes)) {
    await committer.commit(batch);
  }
}

function parseArgs(argv: string[]): { catalogPath: string } {
  const flagIndex = argv.indexOf('--catalog');
  if (flagIndex === -1 || flagIndex === argv.length - 1) {
    throw new Error('Usage: tsx tools/seedCopyCatalog.ts --catalog tools/notification-copy.v1.json');
  }
  return { catalogPath: argv[flagIndex + 1] };
}

async function main(): Promise<void> {
  // Deferred requires: keep these out of the module's static import graph so vitest can import
  // the pure functions above (buildCopyWritePlan/chunkCopyWrites/seedCopyCatalog) without needing
  // an Admin SDK app or file-system access -- this function only runs when invoked directly.
  const { readFileSync } = await import('node:fs');
  const { initializeApp } = await import('firebase-admin/app');
  const { getFirestore } = await import('firebase-admin/firestore');

  const { catalogPath } = parseArgs(process.argv.slice(2));
  const catalog = JSON.parse(readFileSync(catalogPath, 'utf8')) as CopyCatalogFile;

  initializeApp();
  const db = getFirestore();

  const committer: CopyCommitter = {
    async commit(writes) {
      const batch = db.batch();
      for (const write of writes) {
        batch.set(db.doc(write.path), write.data, { merge: true });
      }
      await batch.commit();
    },
  };

  await seedCopyCatalog(catalog, committer);
  console.log(`[seedCopyCatalog] done. version=${catalog.version} variants=${catalog.variants.length}`);
}

if (require.main === module) {
  main().catch((error) => {
    console.error('[seedCopyCatalog] FAILED:', error);
    process.exitCode = 1;
  });
}

// Re-exported for callers that only need the type surface (kept explicit rather than `export *`
// to avoid ambiguity with this file's own value exports).
export type { CopyLocale, CopyVariant, NotificationFamily };
