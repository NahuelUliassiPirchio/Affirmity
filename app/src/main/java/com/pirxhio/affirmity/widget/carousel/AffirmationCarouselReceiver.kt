package com.pirxhio.affirmity.widget.carousel

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Entry point for the carousel widget. Rotation is scheduled with the first instance and cancelled
 * with the last. Per-instance state is removed by Glance's own `onDeleted` handling (D3), so there
 * is deliberately no override for it here.
 */
class AffirmationCarouselReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AffirmationCarouselWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CarouselRotationWorker.enqueue(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        CarouselRotationWorker.cancel(context)
    }
}
