import { describe, expect, it } from 'vitest';

import {
  VARIANT_HISTORY_SIZE,
  buildDeliveryLogUpdate,
  buildVariantHistoryUpdate,
  mergeVariantHistory,
  type DeliveryLogDoc,
} from '../src/notificationState';

// Design §2 (`notificationState.ts`): per-user anti-repeat history (`variantHistory`, size-5,
// most-recent-first) and the sibling `notificationDeliveries/{localDay}` cross-family log.

describe('mergeVariantHistory', () => {
  it('prepends the used key to an empty history', () => {
    expect(mergeVariantHistory(undefined, 'streak_a')).toEqual(['streak_a']);
  });

  it('prepends the used key ahead of existing entries', () => {
    expect(mergeVariantHistory(['streak_b', 'streak_c'], 'streak_a')).toEqual([
      'streak_a',
      'streak_b',
      'streak_c',
    ]);
  });

  it('truncates to VARIANT_HISTORY_SIZE (5), dropping the oldest entries', () => {
    const history = ['h1', 'h2', 'h3', 'h4', 'h5'];
    expect(mergeVariantHistory(history, 'new')).toEqual(['new', 'h1', 'h2', 'h3', 'h4']);
    expect(mergeVariantHistory(history, 'new')).toHaveLength(VARIANT_HISTORY_SIZE);
  });

  it('moves a repeated key to the front instead of duplicating it', () => {
    expect(mergeVariantHistory(['a', 'b', 'c'], 'b')).toEqual(['b', 'a', 'c']);
  });
});

describe('buildVariantHistoryUpdate', () => {
  it('updates only the given family, preserving every other family untouched', () => {
    const current = { streak: ['streak_a'], mood: ['mood_a'] };
    const result = buildVariantHistoryUpdate(current, 'streak', 'streak_b');
    expect(result).toEqual({ streak: ['streak_b', 'streak_a'], mood: ['mood_a'] });
  });

  it('creates the family entry when none exists yet', () => {
    const result = buildVariantHistoryUpdate(undefined, 'reminder', 'reminder_a');
    expect(result).toEqual({ reminder: ['reminder_a'] });
  });
});

describe('buildDeliveryLogUpdate', () => {
  const entry = { deliveredAtMillis: 1_000, variantKey: 'streak_a', destination: 'streak_action' };

  it('creates a new doc for the day when none existed yet', () => {
    const result = buildDeliveryLogUpdate(undefined, 100, 'streak', entry);
    expect(result).toEqual({ localDay: 100, families: { streak: entry } });
  });

  it('adds a family entry alongside existing entries for the same day', () => {
    const current: DeliveryLogDoc = {
      localDay: 100,
      families: { mood: { deliveredAtMillis: 500, variantKey: 'mood_a', destination: 'mood_checkin' } },
    };
    const result = buildDeliveryLogUpdate(current, 100, 'streak', entry);
    expect(result.families.mood).toBeDefined();
    expect(result.families.streak).toEqual(entry);
  });

  it('discards entries from a stale doc belonging to a different local day', () => {
    const staleDoc: DeliveryLogDoc = {
      localDay: 99,
      families: { mood: { deliveredAtMillis: 1, variantKey: 'mood_a', destination: 'mood_checkin' } },
    };
    const result = buildDeliveryLogUpdate(staleDoc, 100, 'streak', entry);
    expect(result).toEqual({ localDay: 100, families: { streak: entry } });
  });

  it('replaces an existing entry for the same family on the same day', () => {
    const current: DeliveryLogDoc = { localDay: 100, families: { streak: entry } };
    const replacement = { deliveredAtMillis: 2_000, variantKey: 'streak_b', destination: 'streak_action' };
    const result = buildDeliveryLogUpdate(current, 100, 'streak', replacement);
    expect(result.families.streak).toEqual(replacement);
  });
});
