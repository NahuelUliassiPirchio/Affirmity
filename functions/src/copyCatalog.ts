/**
 * Server-owned, localized, contextual, non-repeating notification copy (design §1,
 * `notification-copy-catalog` capability). Variant selection and rendering happen entirely at
 * send time in `index.ts`'s `sendNotification` handler -- this module holds the pure,
 * Vitest-testable core plus a thin, memoized Firestore-backed loader.
 *
 * Collection: `notificationCopy/{variantKey}`, server/Admin-SDK-only (`firestore.rules` denies all
 * client read/write). Seeded by `functions/tools/seedCopyCatalog.ts` from
 * `functions/tools/notification-copy.v1.json`.
 */

export type NotificationFamily =
  | 'reminder'
  | 'reflection'
  | 'mood'
  | 'streak'
  | 'healer'
  | 'meditation_return';

export type CopyLocale = 'es' | 'en';

export interface CopyText {
  title: string;
  body: string;
}

export interface CopyVariant {
  /** === Firestore doc id, e.g. `streak_risk_14plus_a`. */
  key: string;
  family: NotificationFamily;
  /** §19 context tags this variant REQUIRES. Empty = matches any context in its family. */
  context: string[];
  /** §18 placeholders that MUST be resolvable (non-empty) before rendering. */
  placeholders: string[];
  locales: Record<CopyLocale, CopyText>;
  enabled: boolean;
  order: number;
}

/**
 * Selects one eligible variant for [family]/[context], avoiding [recentKeys] (per design §2's
 * `variantHistory`, most-recent-first) when possible.
 *
 * Algorithm (design §1):
 * 1. Keep only variants matching [family] whose every declared `context` tag is present in the
 *    caller's current [context].
 * 2. Drop every variant whose key is in [recentKeys].
 * 3. If that empties the pool, retry dropping ONLY `recentKeys[0]` (the single most-recent key) --
 *    reusing an older-but-not-most-recent variant beats returning nothing.
 * 4. If that STILL empties the pool (e.g. exactly one eligible variant total), fall back to the
 *    full eligible pool -- an unavoidable repeat is better than no notification copy at all.
 * 5. Sort the eligible pool by `order` and pick one via [rng] (defaults to `Math.random`).
 */
export function selectVariant(
  pool: CopyVariant[],
  family: NotificationFamily,
  context: string[],
  recentKeys: string[],
  rng: () => number = Math.random,
): CopyVariant | null {
  const familyPool = pool.filter(
    (candidate) =>
      candidate.family === family && candidate.context.every((tag) => context.includes(tag)),
  );
  if (familyPool.length === 0) return null;

  const eligible = resolveEligiblePool(familyPool, recentKeys);
  const sorted = [...eligible].sort((a, b) => a.order - b.order);
  const index = Math.min(sorted.length - 1, Math.max(0, Math.floor(rng() * sorted.length)));
  return sorted[index];
}

function resolveEligiblePool(familyPool: CopyVariant[], recentKeys: string[]): CopyVariant[] {
  const nonRecent = familyPool.filter((candidate) => !recentKeys.includes(candidate.key));
  if (nonRecent.length > 0) return nonRecent;

  const mostRecentKey = recentKeys[0];
  if (mostRecentKey !== undefined) {
    const withoutMostRecent = familyPool.filter((candidate) => candidate.key !== mostRecentKey);
    if (withoutMostRecent.length > 0) return withoutMostRecent;
  }

  return familyPool;
}

/**
 * Renders [variant] in [locale], substituting every `{placeholder}` occurrence with [values].
 * Returns `null` -- never raw `{placeholder}` text -- when any placeholder [variant] DECLARES is
 * missing, `null`/`undefined`, or an empty string (design §1 / spec §18).
 */
export function renderCopy(
  variant: CopyVariant,
  locale: CopyLocale,
  values: Record<string, string>,
): CopyText | null {
  for (const placeholder of variant.placeholders) {
    const value = values[placeholder];
    if (value === undefined || value === null || value === '') return null;
  }

  const copy = variant.locales[locale];
  if (!copy) return null;

  return {
    title: substitutePlaceholders(copy.title, values),
    body: substitutePlaceholders(copy.body, values),
  };
}

function substitutePlaceholders(text: string, values: Record<string, string>): string {
  return text.replace(/\{(\w+)\}/g, (match, key: string) => (key in values ? values[key] : match));
}

/** Firestore read strategy (design §1): one `.where('enabled','==',true).get()` per cold function
 *  instance, memoized in module scope. Abstracted behind [CatalogSource] so this stays testable
 *  without an Admin SDK app -- the real Firestore-backed source lives in `index.ts`. */
export interface CatalogSource {
  loadEnabledVariants(): Promise<CopyVariant[]>;
}

/** 10-minute memoization window (design §1). */
export const CATALOG_CACHE_TTL_MILLIS = 10 * 60 * 1000;

let catalogCache: { loadedAtMillis: number; variants: CopyVariant[] } | null = null;

export async function loadCopyCatalog(
  source: CatalogSource,
  now: () => number = Date.now,
): Promise<CopyVariant[]> {
  if (catalogCache && now() - catalogCache.loadedAtMillis < CATALOG_CACHE_TTL_MILLIS) {
    return catalogCache.variants;
  }
  const variants = await source.loadEnabledVariants();
  catalogCache = { loadedAtMillis: now(), variants };
  return variants;
}

/** Test-only escape hatch -- module-scope cache would otherwise leak state across test cases. */
export function resetCopyCatalogCacheForTests(): void {
  catalogCache = null;
}
