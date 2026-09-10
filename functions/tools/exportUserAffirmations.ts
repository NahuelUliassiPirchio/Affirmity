/**
 * One-off export of a single user's `users/{uid}/affirmations` subcollection to a JSON file — the
 * full doc shape (id, title, subtitle, backgroundType, backgroundValue, groupId, overrides), not
 * just the text, for a user who wants their own personal affirmations out of Firestore without
 * paging through the console by hand.
 *
 * Usage (from `functions/`):
 *   npx tsx tools/exportUserAffirmations.ts --uid <uid> --out /path/to/affirmations.json
 *
 * Authentication is via `GOOGLE_APPLICATION_CREDENTIALS` (standard Admin SDK
 * application-default-credentials resolution), same as `tools/seedCatalog.ts` — no credentials are
 * read or embedded here.
 */

export interface AffirmationDoc {
  id: string;
  title: string;
  subtitle: string;
  backgroundType: string;
  backgroundValue: string;
  groupId?: string;
  overrides?: Record<string, string>;
}

/** Sorted by `groupId` then `title` so re-runs produce a stable, diffable file. */
export function sorted(docs: AffirmationDoc[]): AffirmationDoc[] {
  return [...docs].sort(
    (a, b) => (a.groupId ?? '').localeCompare(b.groupId ?? '') || a.title.localeCompare(b.title),
  );
}

function parseArgs(argv: string[]): { uid: string; outPath: string } {
  const uidIndex = argv.indexOf('--uid');
  const outIndex = argv.indexOf('--out');
  if (uidIndex === -1 || uidIndex === argv.length - 1 || outIndex === -1 || outIndex === argv.length - 1) {
    throw new Error('Usage: tsx tools/exportUserAffirmations.ts --uid <uid> --out /path/to/affirmations.json');
  }
  return { uid: argv[uidIndex + 1], outPath: argv[outIndex + 1] };
}

async function main(): Promise<void> {
  // Deferred requires: keep these out of the module's static import graph so vitest can import
  // `sorted` without needing an Admin SDK app or file-system access.
  const { writeFileSync } = await import('node:fs');
  const { initializeApp } = await import('firebase-admin/app');
  const { getFirestore } = await import('firebase-admin/firestore');

  const { uid, outPath } = parseArgs(process.argv.slice(2));

  initializeApp();
  const db = getFirestore();

  const snapshot = await db.collection(`users/${uid}/affirmations`).get();
  const docs = sorted(snapshot.docs.map((doc) => ({ id: doc.id, ...doc.data() }) as AffirmationDoc));

  writeFileSync(outPath, JSON.stringify(docs, null, 2) + '\n', 'utf8');
  console.log(`[exportUserAffirmations] wrote ${docs.length} affirmation(s) to ${outPath}`);
}

if (require.main === module) {
  main().catch((error) => {
    console.error('[exportUserAffirmations] FAILED:', error);
    process.exitCode = 1;
  });
}
