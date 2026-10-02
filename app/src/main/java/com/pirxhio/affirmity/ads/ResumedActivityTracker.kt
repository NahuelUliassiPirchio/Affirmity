package com.pirxhio.affirmity.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Process-wide record of the CURRENT RESUMED Activity, held weakly. Resolving the Activity at show
 * time from here (instead of from a Context captured by a Composition) means rotation / locale
 * recreation can never leave the ad adapter pointing at a dead Activity, nothing is strongly
 * retained, and an app in the background has no resumed Activity so nothing can be shown.
 */
internal object ResumedActivityTracker {

    @Volatile private var current: WeakReference<Activity>? = null
    private var registered = false

    @Synchronized
    fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                current = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                if (current?.get() === activity) current = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** The resumed, not-finishing Activity, or null (backgrounded / transitioning). */
    fun resumed(): Activity? = current?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
}
