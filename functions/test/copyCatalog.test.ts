import { describe, expect, it } from 'vitest';

import {
  CATALOG_CACHE_TTL_MILLIS,
  loadCopyCatalog,
  renderCopy,
  resetCopyCatalogCacheForTests,
  selectVariant,
  type CopyVariant,
} from '../src/copyCatalog';

/**
 * `selectVariant`/`renderCopy` are the pure, send-time-testable core of design §1
 * (`notification-copy-catalog`). No Firestore, no Admin SDK -- variant pools are plain fixtures.
 */

function variant(overrides: Partial<CopyVariant> = {}): CopyVariant {
  return {
    key: 'v_default',
    family: 'reminder',
    context: [],
    placeholders: [],
    locales: {
      es: { title: 'Titulo ES', body: 'Cuerpo ES' },
      en: { title: 'Title EN', body: 'Body EN' },
    },
    enabled: true,
    order: 1,
    ...overrides,
  };
}

describe('selectVariant', () => {
  it('only selects variants belonging to the requested family', () => {
    const pool = [
      variant({ key: 'reminder_a', family: 'reminder', order: 1 }),
      variant({ key: 'mood_a', family: 'mood', order: 1 }),
    ];
    const result = selectVariant(pool, 'mood', [], [], () => 0);
    expect(result?.key).toBe('mood_a');
  });

  it('filters by context: every tag the variant declares must be present in the current context', () => {
    const pool = [
      variant({ key: 'mood_afternoon', family: 'mood', context: ['afternoon'], order: 1 }),
      variant({ key: 'mood_evening', family: 'mood', context: ['evening'], order: 2 }),
    ];
    const result = selectVariant(pool, 'mood', ['evening'], [], () => 0);
    expect(result?.key).toBe('mood_evening');
  });

  it('excludes recentKeys when at least one non-excluded eligible variant remains', () => {
    const pool = [
      variant({ key: 'streak_a', family: 'streak', order: 1 }),
      variant({ key: 'streak_b', family: 'streak', order: 2 }),
    ];
    const result = selectVariant(pool, 'streak', [], ['streak_a'], () => 0);
    expect(result?.key).toBe('streak_b');
  });

  it('falls back to allowing reuse of everything except the single most-recent key when full exclusion empties the pool', () => {
    const pool = [
      variant({ key: 'streak_a', family: 'streak', order: 1 }),
      variant({ key: 'streak_b', family: 'streak', order: 2 }),
    ];
    // Both keys are "recent" (variantHistory, most-recent first): streak_b is most recent.
    // Dropping both would empty the pool, so the fallback re-admits streak_a (the older one).
    const result = selectVariant(pool, 'streak', [], ['streak_b', 'streak_a'], () => 0);
    expect(result?.key).toBe('streak_a');
  });

  it('falls back to the full family pool when even the single-most-recent fallback would empty it', () => {
    const pool = [variant({ key: 'only_one', family: 'streak', order: 1 })];
    const result = selectVariant(pool, 'streak', [], ['only_one'], () => 0);
    expect(result?.key).toBe('only_one');
  });

  it('picks among eligible variants ordered by `order`, choosing the index the rng maps to', () => {
    const pool = [
      variant({ key: 'a', family: 'reminder', order: 2 }),
      variant({ key: 'b', family: 'reminder', order: 1 }),
      variant({ key: 'c', family: 'reminder', order: 3 }),
    ];
    // sorted by order: b(1), a(2), c(3) -- rng() close to 1 must land on the LAST eligible entry
    const result = selectVariant(pool, 'reminder', [], [], () => 0.999);
    expect(result?.key).toBe('c');

    const first = selectVariant(pool, 'reminder', [], [], () => 0);
    expect(first?.key).toBe('b');
  });

  it('returns null when no variant matches the requested family', () => {
    const pool = [variant({ key: 'mood_a', family: 'mood' })];
    expect(selectVariant(pool, 'streak', [], [], () => 0)).toBeNull();
  });
});

describe('renderCopy', () => {
  it('substitutes every declared placeholder with the provided value', () => {
    const streakVariant = variant({
      key: 'streak_risk_14plus_a',
      family: 'streak',
      placeholders: ['streakCount'],
      locales: {
        es: { title: '{streakCount} días. No la dejes caer ahora 🔥', body: 'Body ES' },
        en: { title: '{streakCount} days. Don\'t let it drop now 🔥', body: 'Body EN' },
      },
    });
    const rendered = renderCopy(streakVariant, 'es', { streakCount: '20' });
    expect(rendered).toEqual({ title: '20 días. No la dejes caer ahora 🔥', body: 'Body ES' });
  });

  it('returns null when a declared placeholder value is missing', () => {
    const streakVariant = variant({ key: 'streak_a', placeholders: ['streakCount'] });
    expect(renderCopy(streakVariant, 'es', {})).toBeNull();
  });

  it('returns null when a declared placeholder value is an empty string', () => {
    const streakVariant = variant({ key: 'streak_a', placeholders: ['streakCount'] });
    expect(renderCopy(streakVariant, 'es', { streakCount: '' })).toBeNull();
  });

  it('never emits raw declared-placeholder syntax in the rendered output', () => {
    const streakVariant = variant({
      key: 'streak_a',
      placeholders: ['streakCount'],
      locales: {
        es: { title: '{streakCount} días', body: 'Body' },
        en: { title: '{streakCount} days', body: 'Body' },
      },
    });
    const rendered = renderCopy(streakVariant, 'en', { streakCount: '7' });
    expect(rendered?.title).not.toContain('{streakCount}');
    expect(rendered?.title).toBe('7 days');
  });
});

describe('loadCopyCatalog', () => {
  it('loads from the source on first call and memoizes the result within the TTL', async () => {
    resetCopyCatalogCacheForTests();
    let loadCount = 0;
    const source = {
      loadEnabledVariants: async () => {
        loadCount++;
        return [variant({ key: 'a' })];
      },
    };
    let now = 1_000_000;

    const first = await loadCopyCatalog(source, () => now);
    now += 1000; // still well within the TTL
    const second = await loadCopyCatalog(source, () => now);

    expect(first).toEqual(second);
    expect(loadCount).toBe(1);
  });

  it('reloads from the source once the TTL has elapsed', async () => {
    resetCopyCatalogCacheForTests();
    let loadCount = 0;
    const source = {
      loadEnabledVariants: async () => {
        loadCount++;
        return [variant({ key: `variant_${loadCount}` })];
      },
    };
    let now = 2_000_000;

    const first = await loadCopyCatalog(source, () => now);
    now += CATALOG_CACHE_TTL_MILLIS + 1;
    const second = await loadCopyCatalog(source, () => now);

    expect(loadCount).toBe(2);
    expect(first[0].key).toBe('variant_1');
    expect(second[0].key).toBe('variant_2');
  });
});
