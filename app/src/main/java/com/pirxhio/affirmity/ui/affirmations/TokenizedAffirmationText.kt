package com.pirxhio.affirmity.ui.affirmations

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.pirxhio.affirmity.data.AffirmationTemplate
import com.pirxhio.affirmity.data.AffirmationTemplateParser
import com.pirxhio.affirmity.data.TemplateSegment

/**
 * Proportional-font width approximation for the inline edit field (design.md Open Question: needs
 * a visual pass on device to tune).
 */
private const val TOKEN_FIELD_EM_PER_CHAR = 0.62f

/** Shared background/typography styling for every rendered token, both surfaces (design.md D12). */
val defaultTokenStyle: SpanStyle
    @Composable get() = SpanStyle(
        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
    )

/**
 * Renders [template] as one flowing [Text], literal segments plain and token segments styled
 * with [tokenStyle]. When [editable], tapping a token swaps it in place for an inline text field
 * (design.md D9-D11): single active editor, commit on blur / IME-Done / system Back, empty input
 * reverts to the authored original.
 */
@Composable
fun TokenizedAffirmationText(
    template: AffirmationTemplate,
    overrides: Map<String, String>,
    style: TextStyle,
    color: Color,
    tokenStyle: SpanStyle,
    editable: Boolean,
    onOverrideCommitted: (tokenKey: String, value: String) -> Unit,
    textAlign: TextAlign? = null,
    modifier: Modifier = Modifier,
) {
    var editingKey by remember(template) { mutableStateOf<String?>(null) }
    var editingValue by remember(template) { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember(template) { FocusRequester() }

    fun commit() {
        val key = editingKey ?: return
        val raw = editingValue.text
        val normalized = AffirmationTemplateParser.normalizeOverrideValue(raw)
        if (normalized != overrides[key]) {
            // blank raw normalizes to null -> onOverrideCommitted's receiver removes the key.
            onOverrideCommitted(key, raw)
        }
        editingKey = null
    }

    fun startEditing(token: TemplateSegment.Token) {
        if (editingKey != null && editingKey != token.key) {
            commit()
        }
        val value = template.valueOf(token, overrides)
        editingKey = token.key
        editingValue = TextFieldValue(text = value, selection = TextRange(0, value.length))
    }

    if (editable && editingKey != null) {
        BackHandler(onBack = ::commit)
    }

    val annotated = buildAnnotatedString {
        for (segment in template.segments) {
            when (segment) {
                is TemplateSegment.Literal -> append(segment.text)
                is TemplateSegment.Token -> when {
                    editable && segment.key == editingKey ->
                        appendInlineContent(segment.key, alternateText = editingValue.text)

                    editable -> withLink(
                        LinkAnnotation.Clickable(
                            tag = segment.key,
                            styles = TextLinkStyles(style = tokenStyle),
                        ) { startEditing(segment) },
                    ) {
                        append(template.valueOf(segment, overrides))
                    }

                    else -> withStyle(tokenStyle) {
                        append(template.valueOf(segment, overrides))
                    }
                }
            }
        }
    }

    val currentEditingKey = editingKey
    val inlineContent = if (editable && currentEditingKey != null) {
        mapOf(
            currentEditingKey to InlineTextContent(
                placeholder = Placeholder(
                    width = ((editingValue.text.length + 1).coerceAtLeast(3) * TOKEN_FIELD_EM_PER_CHAR).em,
                    height = 1.35.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            ) {
                LaunchedEffect(currentEditingKey) { focusRequester.requestFocus() }
                BasicTextField(
                    value = editingValue,
                    onValueChange = { editingValue = it },
                    singleLine = true,
                    textStyle = style.merge(tokenStyle),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState -> if (!focusState.isFocused) commit() },
                )
            },
        )
    } else {
        emptyMap()
    }

    Text(
        text = annotated,
        style = style,
        color = color,
        textAlign = textAlign,
        inlineContent = inlineContent,
        modifier = modifier,
    )
}
