package com.pirxhio.affirmity.data.catalog

/**
 * The bracket gate's RULE (design D11). Pure, no Android, no I/O, no rewrite logic: the source
 * corpus is measured clean (zero literal `[`/`]` across all 2712 texts), so this is a
 * VERIFICATION gate, not a fix-forward step. If a future content drop introduces a bracket, the
 * generator/build fails loudly and a human edits the content.
 */
object CatalogTextSanitizer {

    /** Character offsets of every literal `[` or `]` in [text]. Empty means clean. */
    fun findIllegalBrackets(text: String): List<Int> =
        text.indices.filter { i -> text[i] == '[' || text[i] == ']' }
}
