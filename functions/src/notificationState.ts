/**
 * Per-user anti-repeat + cooldown state (design §2, `notification-orchestration`'s supporting
 * schema). Read once and merge-written once per send inside `sendNotification` (`index.ts`,
 * Phase 4) -- this module holds only the pure, Vitest-testable merge/build logic. Firestore
 * wiring lives in `index.ts`, matching `streak.ts`/`healer.ts`'s pure-core convention.
 *
 * Collections:
 *  - `users/{uid}/notificationState/current`          -- anti-repeat history + meditation-return
 *    cooldown, owner-read / server-write-only (`firestore.rules`)
 *  - `users/{uid}/notificationDeliveries/{localDay}`   -- today's per-family delivery log, read by
 *    design §3's send-time cross-family rules (e.g. Compass-too-soon-after-Mood)
 */

import type { NotificationFamily } from './copyCatalog';
import type { MeditationReturnBand, MeditationReturnState } from './meditationReturn';

/** Last N used variant keys kept per family, most-recent FIRST (design §2). */
export const VARIANT_HISTORY_SIZE = 5;

/**
 * Design §4's meditation-return cooldown state. Single source of truth lives in
 * `meditationReturn.ts` (Phase 3) -- re-exported here so `NotificationStateDoc` consumers don't
 * need to import from two modules.
 */
export type { MeditationReturnBand, MeditationReturnState };

export interface NotificationStateDoc {
  variantHistory: Partial<Record<NotificationFamily, string[]>>;
  meditationReturn?: MeditationReturnState;
}

/**
 * Prepends [usedKey] to [history] (design §2). Any earlier occurrence of [usedKey] is removed
 * first, so a repeated key moves to the front instead of appearing twice, then the result is
 * truncated to [VARIANT_HISTORY_SIZE].
 */
export function mergeVariantHistory(history: string[] | undefined, usedKey: string): string[] {
  const withoutUsedKey = (history ?? []).filter((key) => key !== usedKey);
  return [usedKey, ...withoutUsedKey].slice(0, VARIANT_HISTORY_SIZE);
}

/**
 * Builds the full `variantHistory` map to `set(..., { merge: true })` after a successful send:
 * every other family's history is carried over untouched -- only [family] is updated.
 */
export function buildVariantHistoryUpdate(
  current: Partial<Record<NotificationFamily, string[]>> | undefined,
  family: NotificationFamily,
  usedKey: string,
): Partial<Record<NotificationFamily, string[]>> {
  return {
    ...current,
    [family]: mergeVariantHistory(current?.[family], usedKey),
  };
}

export interface DeliveryLogEntry {
  deliveredAtMillis: number;
  variantKey: string;
  destination: string;
}

export interface DeliveryLogDoc {
  localDay: number;
  families: Partial<Record<NotificationFamily, DeliveryLogEntry>>;
}

/**
 * Builds the full `notificationDeliveries/{localDay}` doc to `set(..., { merge: true })` after a
 * successful send. When [current] belongs to a different local day than [localDay] (a stale read,
 * or none at all), its `families` are discarded rather than carried over -- each day's delivery
 * log must only ever describe that day.
 */
export function buildDeliveryLogUpdate(
  current: DeliveryLogDoc | undefined,
  localDay: number,
  family: NotificationFamily,
  entry: DeliveryLogEntry,
): DeliveryLogDoc {
  const families = current?.localDay === localDay ? current.families : {};
  return {
    localDay,
    families: { ...families, [family]: entry },
  };
}
