package com.pirxhio.affirmity.data.catalog

import com.pirxhio.affirmity.data.AffirmationTemplateParser

/**
 * The bracket gate's rule (design D1): a bracket sequence is LEGAL only if it forms a non-blank
 * token -- everything else (unbalanced, empty, or blank-only bracket content) is illegal.
 *
 * keep in lockstep with tools/catalog/bracketGate.mjs
 *
 * Consumes [AffirmationTemplateParser.TOKEN_REGEX] directly so the "what counts as a token" rule
 * is expressed exactly once for Kotlin; masks every match whose content `isNotBlank()`, then
 * returns the offsets of any `[`/`]` left over. Deliberately stricter than
 * [AffirmationTemplateParser], which only demotes `content.isEmpty()` (not blank-only) -- gate-
 * passing text is therefore always a strict subset of parseable text, never the reverse.
 */
object CatalogTextSanitizer {

    /** Character offsets of every residual `[`/`]` in [text] after masking legal tokens. Empty means clean. */
    fun findIllegalBrackets(text: String): List<Int> {
        val masked = StringBuilder(text)
        for (match in AffirmationTemplateParser.TOKEN_REGEX.findAll(text)) {
            val content = match.groupValues[1]
            if (content.isNotBlank()) {
                for (i in match.range) {
                    masked.setCharAt(i, ' ')
                }
            }
        }
        return masked.indices.filter { i -> masked[i] == '[' || masked[i] == ']' }
    }
}
