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

    /** Excludes both bracket chars from the content class (D3): `[a[b]` -> literal `[a` + token `b`. */
    private val TOKEN_REGEX = Regex("""\[([^\[\]]*)]""")

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
            segments.add(TemplateSegment.Token(tokenKey(field, ordinal, content), content))
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

    fun tokenKey(field: TemplateField, ordinal: Int, original: String): String =
        "${field.prefix}:$ordinal:$original"

    /** Drops override keys with no matching token in the current text (D1 / locked decision #7). */
    fun pruneOverrides(
        title: String,
        subtitle: String,
        overrides: Map<String, String>,
    ): Map<String, String> {
        val currentKeys = parse(TemplateField.TITLE, title).tokenKeys.toSet() +
            parse(TemplateField.SUBTITLE, subtitle).tokenKeys.toSet()
        return overrides.filterKeys { it in currentKeys }
    }

    /** Commit-time normalization: trims, drops blanks, enforces [MAX_OVERRIDE_VALUE_LENGTH]. */
    fun normalizeOverrideValue(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.take(MAX_OVERRIDE_VALUE_LENGTH)
    }
}
