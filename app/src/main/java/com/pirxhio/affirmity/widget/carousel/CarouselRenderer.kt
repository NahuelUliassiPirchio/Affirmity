package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.data.AffirmationTemplateParser
import com.pirxhio.affirmity.data.OverrideKeys
import com.pirxhio.affirmity.data.TemplateField
import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
import kotlinx.coroutines.CancellationException

/** Turns a queue entry into displayable text, loading text only for the frame being shown. */
class CarouselFrameResolver(private val catalogRow: suspend (String) -> CatalogAffirmationEntity?) {

    suspend fun resolve(
        ref: CarouselRef,
        result: PoolResult,
        ownedById: Map<String, AffirmationEntity>,
    ): CarouselFrame? {
        val (title, subtitle) = if (ref.collectionId == null) {
            val owned = ownedById[ref.id] ?: return null
            val overrides = OverrideKeys.displayView(owned.overrides)
            render(owned.title, owned.subtitle, overrides)
        } else {
            val row = catalogRow(ref.id) ?: return null
            render(row.text, row.subtitle, emptyMap())
        }
        return CarouselFrame(
            text = title,
            subtitle = subtitle.takeIf { it.isNotBlank() },
            notice = (result as? PoolResult.Fallback)?.reason,
            isFreeTier = ref.isFreeTier,
        )
    }

    private fun render(title: String, subtitle: String, overrides: Map<String, String>): Pair<String, String> =
        AffirmationTemplateParser.parse(TemplateField.TITLE, title).render(overrides) to
            AffirmationTemplateParser.parse(TemplateField.SUBTITLE, subtitle).render(overrides)
}

/** Result of one render: the state to persist and the frame to draw (`null` = placeholder). */
data class RenderOutcome(val state: CarouselState, val frame: CarouselFrame?)

/** Pure orchestration of one widget render: pool -> queue -> frame -> state to persist. */
class CarouselRenderer(
    private val queue: ShuffleQueue,
    private val resolver: CarouselFrameResolver,
) {
    suspend fun render(
        state: CarouselState,
        advance: Boolean,
        load: CarouselLoad,
        nowMillis: Long,
        account: String,
    ): RenderOutcome {
        val result = CarouselPoolBuilder.build(state.source, load.snapshot, load.access, nowMillis)

        // A stored frame that belongs to a different account must never reach this one.
        val scoped = state.forAccount(account)

        // Owned data could not be read: keep showing the last text, but only if it was free and
        // belongs to this account.
        val replay = scoped.replayableLast(account)
        if (result is PoolResult.Fallback && result.reason == FallbackReason.DATA_UNAVAILABLE && replay != null) {
            return RenderOutcome(scoped, CarouselFrame(replay.text, replay.subtitle, null, isFreeTier = true))
        }

        val next = CarouselRotation.next(scoped, result.refs.map { it.id }, queue, advance)
        val current = next.queue.currentOrNull()
        val ref = result.refs.firstOrNull { it.id == current }
        val frame = ref?.let { resolver.resolve(it, result, load.ownedById) }
        val withFlag = next.copy(fallback = result is PoolResult.Fallback)
        return RenderOutcome(if (frame != null) withFlag.withShown(frame, account) else withFlag, frame)
    }
}

/**
 * Runs [render] so that no failure can blank the widget or crash the host. [resolveAccount] and
 * [render] both run inside the guard; on failure the last free frame is replayed only when it
 * belongs to the same account, otherwise the placeholder is shown and the stored frame dropped.
 * [CancellationException] is always rethrown.
 */
suspend fun renderGuarded(
    state: CarouselState,
    resolveAccount: () -> String,
    onError: (Exception) -> Unit = {},
    render: suspend (account: String) -> RenderOutcome,
): RenderOutcome {
    var account: String? = null
    return try {
        account = resolveAccount()
        render(account)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
        val scoped = state.forAccount(account)
        val last = account?.let { scoped.replayableLast(it) }
        RenderOutcome(scoped, last?.let { CarouselFrame(it.text, it.subtitle, null, isFreeTier = true) })
    }
}
