package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.data.AffirmationTemplateParser
import com.pirxhio.affirmity.data.OverrideKeys
import com.pirxhio.affirmity.data.TemplateField
import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity

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
    ): RenderOutcome {
        val result = CarouselPoolBuilder.build(state.source, load.snapshot, load.access, nowMillis)

        // Owned data could not be read: keep showing the last text, but only if it was free (R6.3).
        val replay = state.replayableLast()
        if (result is PoolResult.Fallback && result.reason == FallbackReason.DATA_UNAVAILABLE && replay != null) {
            return RenderOutcome(state, CarouselFrame(replay.text, replay.subtitle, null, isFreeTier = true))
        }

        val next = CarouselRotation.next(state, result.refs.map { it.id }, queue, advance)
        val current = next.queue.currentOrNull()
        val ref = result.refs.firstOrNull { it.id == current }
        val frame = ref?.let { resolver.resolve(it, result, load.ownedById) }
        val withFlag = next.copy(fallback = result is PoolResult.Fallback)
        return RenderOutcome(if (frame != null) withFlag.withShown(frame) else withFlag, frame)
    }
}
