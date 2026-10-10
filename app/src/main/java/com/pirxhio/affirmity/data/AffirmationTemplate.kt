package com.pirxhio.affirmity.data

/** Which authored field a token came from — keeps title/subtitle key namespaces disjoint (D1). */
enum class TemplateField(val prefix: String) { TITLE("title"), SUBTITLE("subtitle") }

sealed interface TemplateSegment {
    data class Literal(val text: String) : TemplateSegment

    /** [key] is stable-by-construction (D1); [original] is the verbatim bracketed content. */
    data class Token(val key: String, val original: String) : TemplateSegment
}

/** Maximum length, in characters, an override value may occupy after normalization (D15). */
const val MAX_OVERRIDE_VALUE_LENGTH = 120

data class AffirmationTemplate(val field: TemplateField, val segments: List<TemplateSegment>) {
    val tokenKeys: List<String>
        get() = segments.filterIsInstance<TemplateSegment.Token>().map { it.key }

    /** Effective value for a token: override if present and non-blank, else the authored original. */
    fun valueOf(token: TemplateSegment.Token, overrides: Map<String, String>): String =
        overrides[token.key]?.takeIf { it.isNotBlank() } ?: token.original

    /** Flat rendered text. `render(emptyMap())` yields the original values with brackets stripped. */
    fun render(overrides: Map<String, String>): String = buildString {
        for (segment in segments) {
            when (segment) {
                is TemplateSegment.Literal -> append(segment.text)
                is TemplateSegment.Token -> append(valueOf(segment, overrides))
            }
        }
    }
}

/**
 * Pure, stdlib-only parser turning authored `[token]` text into structured segments and
 * resolving override maps against them. No Android, no Room, no Firebase (D2).
 */
object AffirmationTemplateParser {

    /** Excludes both bracket chars from the content class (D3): `[a[b]` -> literal `[a` + token `b`.
     *  internal: also consumed by [com.pirxhio.affirmity.data.catalog.CatalogTextSanitizer] (D1) to
     *  mask legal tokens before flagging residual brackets -- keep in lockstep with
     *  `tools/catalog/bracketGate.mjs`'s `TOKEN_REGEX`. */
    internal val TOKEN_REGEX = Regex("""\[([^\[\]]*)]""")

    fun parse(field: TemplateField, text: String): AffirmationTemplate {
        val segments = mutableListOf<TemplateSegment>()
        var cursor = 0
        var ordinal = 0

        for (match in TOKEN_REGEX.findAll(text)) {
            val content = match.groupValues[1]
            if (content.isEmpty()) {
                // A `[]` with blank content is demoted to a literal — no meaningful default value.
                continue
            }

            val literalBefore = text.substring(cursor, match.range.first)
            if (literalBefore.isNotEmpty()) {
                segments.add(TemplateSegment.Literal(literalBefore))
            }
            segments.add(TemplateSegment.Token(tokenKey(field, ordinal), content))
            ordinal++
            cursor = match.range.last + 1
        }

        val trailing = text.substring(cursor)
        if (trailing.isNotEmpty()) {
            segments.add(TemplateSegment.Literal(trailing))
        }

        if (segments.isEmpty()) {
            segments.add(TemplateSegment.Literal(text))
        }

        return AffirmationTemplate(field, segments)
    }

    /** Language-independent slot key `field:ordinal` (REQ-OVR-1); the authored text is not part of it. */
    fun tokenKey(field: TemplateField, ordinal: Int): String = "${field.prefix}:$ordinal"

    /** Commit-time normalization: trims, drops blanks, enforces [MAX_OVERRIDE_VALUE_LENGTH]. */
    fun normalizeOverrideValue(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.take(MAX_OVERRIDE_VALUE_LENGTH)
    }
}

/**
 * Override key grammar and lossless write rules (REQ-OVR-1..5). Pure, stdlib-only.
 *
 * - CANONICAL `field:n`: `n` is a normalized decimal (`n.toString() == part`), `n >= 0`.
 * - LEGACY `field:n:original`: same field/ordinal rules, 3 parts (`original` may contain `:`).
 * - UNKNOWN: anything else; never shown, never rewritten, never pruned.
 */
object OverrideKeys {

    private data class Slot(val field: TemplateField, val ordinal: Int)

    private data class Parsed(val slot: Slot, val legacy: Boolean)

    private fun classify(key: String): Parsed? {
        val parts = key.split(':', limit = 3)
        if (parts.size !in 2..3) return null
        val field = TemplateField.entries.firstOrNull { it.prefix == parts[0] } ?: return null
        val ordinal = parts[1].toIntOrNull()?.takeIf { it >= 0 && it.toString() == parts[1] } ?: return null
        return Parsed(Slot(field, ordinal), legacy = parts.size == 3)
    }

    private fun canonicalKey(slot: Slot) = AffirmationTemplateParser.tokenKey(slot.field, slot.ordinal)

    /** Winning legacy key of a slot: the lexicographically smallest full key string. */
    private fun winningLegacy(stored: Map<String, String>, slot: Slot): String? =
        stored.keys.filter { classify(it) == Parsed(slot, legacy = true) }.minOrNull()

    /**
     * Read-only rendering/editing view: canonical value per slot, else the smallest legacy key's
     * value. Unknown keys are hidden. Never mutates storage (S19).
     */
    fun displayView(stored: Map<String, String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val legacyBySlot = mutableMapOf<Slot, String>()
        for ((key, value) in stored) {
            val parsed = classify(key) ?: continue
            if (parsed.legacy) {
                val current = legacyBySlot[parsed.slot]
                if (current == null || key < current) legacyBySlot[parsed.slot] = key
            } else {
                result[key] = value
            }
        }
        for ((slot, legacyKey) in legacyBySlot) {
            result.putIfAbsent(canonicalKey(slot), stored.getValue(legacyKey))
        }
        return result
    }

    /** Non-empty token count per field of the current template text. */
    fun tokenCounts(title: String, subtitle: String): Map<TemplateField, Int> = mapOf(
        TemplateField.TITLE to AffirmationTemplateParser.parse(TemplateField.TITLE, title).tokenKeys.size,
        TemplateField.SUBTITLE to AffirmationTemplateParser.parse(TemplateField.SUBTITLE, subtitle).tokenKeys.size,
    )

    /**
     * Pure write rule applied to the RAW stored map: migrate in-range legacy slots to canonical
     * (selected legacy key only), apply the edit (null = user revert, which also removes every
     * legacy key of that slot), then prune out-of-range CANONICAL keys. Nothing else is deleted.
     */
    fun applyEdit(
        stored: Map<String, String>,
        field: TemplateField,
        ordinal: Int,
        value: String?,
        tokenCounts: Map<TemplateField, Int>,
    ): Map<String, String> {
        val editedSlot = Slot(field, ordinal)
        val result = LinkedHashMap(stored)

        fun inRange(slot: Slot) = slot.ordinal < (tokenCounts[slot.field] ?: 0)

        // 1. Migrate legacy -> canonical for in-range slots lacking a canonical key. The edited slot
        // is skipped on a set (its legacy keys stay shadowed) and removed wholesale on a revert.
        val legacySlots = stored.keys.mapNotNull { key -> classify(key)?.takeIf { it.legacy }?.slot }.toSet()
        for (slot in legacySlots) {
            if (slot == editedSlot || !inRange(slot)) continue
            if (canonicalKey(slot) in result) continue
            val legacyKey = winningLegacy(stored, slot) ?: continue
            result[canonicalKey(slot)] = stored.getValue(legacyKey)
            result.remove(legacyKey)
        }

        // 2. Edit.
        if (value != null) {
            result[canonicalKey(editedSlot)] = value
        } else {
            result.remove(canonicalKey(editedSlot))
            result.keys.removeAll { classify(it) == Parsed(editedSlot, legacy = true) }
        }

        // 3. Prune out-of-range canonical keys only.
        result.keys.removeAll { key ->
            val parsed = classify(key)
            parsed != null && !parsed.legacy && !inRange(parsed.slot)
        }
        return result
    }
}
