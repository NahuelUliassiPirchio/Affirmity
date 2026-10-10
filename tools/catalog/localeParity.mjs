/**
 * Locale parity gate (REQ-PAR-3..5). Pure: compares two `csvToSource` outputs (base = es, other =
 * another locale) and throws on any structural drift, so a token ordinal means the same slot in
 * every language. Token ORDER cannot be machine-checked; fields with more than one token are
 * returned as warnings for a human reviewer (design D6).
 */
import { countTokens } from "./bracketGate.mjs";

const METADATA_FIELDS = ["collectionId", "themeId", "universeId", "tone", "semanticAngle", "order"];
const TEXT_FIELDS = ["title", "subtitle"];

function fail(message) {
  throw new Error(`[localeParity] FAILED: ${message}`);
}

/**
 * @returns {{warnings: string[]}}
 * @throws on id set / order / metadata / token-count mismatch
 */
export function assertLocaleParity(base, other) {
  if (base.catalogVersion !== other.catalogVersion) {
    fail(`catalogVersion differs: ${base.catalogVersion} vs ${other.catalogVersion}`);
  }

  const baseIds = base.affirmations.map((a) => a.id);
  const otherIds = other.affirmations.map((a) => a.id);
  const baseSet = new Set(baseIds);
  const otherSet = new Set(otherIds);
  const missing = baseIds.filter((id) => !otherSet.has(id));
  const extra = otherIds.filter((id) => !baseSet.has(id));
  if (missing.length > 0 || extra.length > 0) {
    const parts = [];
    if (missing.length > 0) parts.push(`missing in other locale: ${missing.join(", ")}`);
    if (extra.length > 0) parts.push(`extra in other locale: ${extra.join(", ")}`);
    fail(`id sets differ (${parts.join("; ")})`);
  }

  for (let i = 0; i < baseIds.length; i++) {
    if (baseIds[i] !== otherIds[i]) {
      fail(`id order differs at position ${i}: base "${baseIds[i]}" vs other "${otherIds[i]}"`);
    }
  }

  const warnings = [];
  for (let i = 0; i < base.affirmations.length; i++) {
    const a = base.affirmations[i];
    const b = other.affirmations[i];
    for (const field of METADATA_FIELDS) {
      if (a[field] !== b[field]) {
        fail(`metadata differs for id=${a.id} field=${field}: "${a[field]}" vs "${b[field]}"`);
      }
    }
    for (const field of TEXT_FIELDS) {
      const baseCount = countTokens(a[field] ?? "");
      const otherCount = countTokens(b[field] ?? "");
      if (baseCount !== otherCount) {
        fail(`token count differs for id=${a.id} field=${field}: base=${baseCount} other=${otherCount}`);
      }
      if (baseCount > 1) {
        warnings.push(`id=${a.id} field=${field} has ${baseCount} tokens: verify token order matches across locales`);
      }
    }
  }
  return { warnings };
}
