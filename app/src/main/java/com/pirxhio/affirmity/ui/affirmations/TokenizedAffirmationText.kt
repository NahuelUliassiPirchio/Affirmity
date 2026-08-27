package com.pirxhio.affirmity.ui.affirmations

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextLayoutResult
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
import kotlinx.coroutines.delay

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
    favoriteTapEnabled: Boolean = false,
    onFavoriteToggleFromToken: () -> Unit = {},
    textAlign: TextAlign? = null,
    modifier: Modifier = Modifier,
) {
    var editingKey by remember(template) { mutableStateOf<String?>(null) }
    var editingValue by remember(template) { mutableStateOf(TextFieldValue()) }
    var pendingEditKey by remember(template) { mutableStateOf<String?>(null) }
    var textLayoutResult by remember(template) { mutableStateOf<TextLayoutResult?>(null) }
    val favoriteTapCoordinator = remember(template, favoriteTapEnabled) {
        FavoriteTokenTapCoordinator(favoriteTapEnabled = favoriteTapEnabled)
    }
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

    fun startEditing(key: String) {
        template.segments
            .filterIsInstance<TemplateSegment.Token>()
            .firstOrNull { it.key == key }
            ?.let { token -> startEditing(token) }
    }

    LaunchedEffect(pendingEditKey) {
        val key = pendingEditKey ?: return@LaunchedEffect
        delay(FavoriteTapArbiter.DEFAULT_DOUBLE_TAP_WINDOW_MILLIS)
        pendingEditKey = null
        startEditing(key)
    }

    if (editable && editingKey != null) {
        BackHandler(onBack = ::commit)
    }

    val clickableTokenRanges = mutableListOf<ClickableTokenRange>()
    val annotated = buildAnnotatedString {
        for (segment in template.segments) {
            when (segment) {
                is TemplateSegment.Literal -> append(segment.text)
                is TemplateSegment.Token -> when {
                    editable && segment.key == editingKey ->
                        appendInlineContent(segment.key, alternateText = editingValue.text)

                    editable -> {
                        val start = length
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = segment.key,
                                styles = TextLinkStyles(style = tokenStyle),
                            ) {
                                when (
                                    val decision = favoriteTapCoordinator.onTokenClick(
                                        segment.key,
                                        callbackAtMillis = System.currentTimeMillis(),
                                    )
                                ) {
                                    is TokenTapDecision.Wait -> pendingEditKey = decision.key
                                    is TokenTapDecision.StartEditing -> startEditing(decision.key)
                                    TokenTapDecision.ToggleFavorite -> {
                                        pendingEditKey = null
                                        onFavoriteToggleFromToken()
                                    }
                                }
                            },
                        ) {
                            append(template.valueOf(segment, overrides))
                        }
                        clickableTokenRanges += ClickableTokenRange(segment.key, start, length)
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

    val favoritePointerModifier = if (favoriteTapEnabled) {
        Modifier.pointerInput(annotated) {
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                val offset = textLayoutResult?.getOffsetForPosition(down.position)
                    ?: return@awaitEachGesture
                val token = clickableTokenRanges.firstOrNull { offset in it.start until it.end }
                    ?: return@awaitEachGesture
                if (favoriteTapCoordinator.onPointerDown(token.key, down.uptimeMillis)) {
                    pendingEditKey = null
                }
            }
        }
    } else {
        Modifier
    }

    Text(
        text = annotated,
        style = style,
        color = color,
        textAlign = textAlign,
        inlineContent = inlineContent,
        onTextLayout = { textLayoutResult = it },
        modifier = modifier.then(favoritePointerModifier),
    )
}

private data class ClickableTokenRange(
    val key: String,
    val start: Int,
    val end: Int,
)
