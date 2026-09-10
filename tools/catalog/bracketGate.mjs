/**
 * The bracket gate's rule (design D1), JS twin of `CatalogTextSanitizer.kt`.
 *
 * keep in lockstep with app/src/main/java/com/pirxhio/affirmity/data/catalog/CatalogTextSanitizer.kt
 *
 * A bracket sequence is LEGAL only if it forms a non-blank token: `/\[([^\[\]]*)]/g` matched with
 * `content.trim() !== ''`. Every legal match is masked out first; any `[`/`]` left over is illegal.
 * This is deliberately stricter than `AffirmationTemplateParser` (Kotlin), which only demotes
 * `content.isEmpty()`, not blank-only content -- gate-passing text is always a strict subset of
 * parseable text, never the reverse.
 */

const TOKEN_REGEX = /\[([^[\]]*)]/g;

/** Character offsets of every residual `[`/`]` in [text] after masking legal tokens. Empty means clean. */
export function findIllegalBrackets(text) {
  const masked = text.replace(TOKEN_REGEX, (match, content) => {
    if (content.trim() !== '') {
      return ' '.repeat(match.length);
    }
    return match;
  });

  const offsets = [];
  for (let i = 0; i < masked.length; i++) {
    const c = masked[i];
    if (c === '[' || c === ']') offsets.push(i);
  }
  return offsets;
}
