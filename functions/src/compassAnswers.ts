/**
 * Per-user Compass-answer tracking (design D9). Backs the send-time `compassAnsweredToday` check
 * in design §3's suppression table (`notification-orchestration`) and records which question was
 * answered, so a client-side cancel-on-answer and any future analytics can attribute the answer to
 * the exact prompt that was sent (Compass notifications are addressed by question id -- see the
 * "Per-Instance Deep Links" requirement in spec.md).
 *
 * Collection: `users/{uid}/compassAnswers/{localDay}`, one document per local day, owner-read /
 * server-write-only (`firestore.rules`). Firestore wiring lives in `index.ts` (Phase 4); this
 * module holds only the pure, Vitest-testable shape + predicate + builder, matching
 * `streak.ts`/`healer.ts`/`notificationState.ts`'s pure-core convention.
 */

export interface CompassAnswerDoc {
  localDay: number;
  /** The Compass question that was outstanding when answered. */
  questionId: string;
  answeredAtMillis: number;
}

/**
 * True only when [doc] records an answer for [localDay] specifically -- a doc describing a
 * *different* (stale) local day MUST NOT count as "answered today".
 */
export function wasCompassAnsweredToday(
  doc: CompassAnswerDoc | null | undefined,
  localDay: number,
): boolean {
  return doc?.localDay === localDay;
}

/** Builds the doc to `set()` when the user answers today's Compass question. */
export function buildCompassAnswerDoc(
  localDay: number,
  questionId: string,
  answeredAtMillis: number,
): CompassAnswerDoc {
  return { localDay, questionId, answeredAtMillis };
}
