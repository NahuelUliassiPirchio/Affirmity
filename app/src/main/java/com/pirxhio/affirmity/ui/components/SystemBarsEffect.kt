package com.pirxhio.affirmity.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Shows or hides the system bars for as long as this composable is in the composition. Hidden
 * bars come back on a swipe (transient). On dispose the bars are always re-shown and the previous
 * systemBarsBehavior is restored, so neither leaks to other screens.
 */
@Composable
fun SystemBarsEffect(visible: Boolean) {
    val context = LocalContext.current
    DisposableEffect(visible) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousBehavior = controller?.systemBarsBehavior
        if (visible) {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previousBehavior != null) controller.systemBarsBehavior = previousBehavior
        }
    }
}

/** Walks the [ContextWrapper] chain to the hosting [Activity]; unlike the ads helper it does not filter finishing ones, since bars must be restored regardless. */
private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
