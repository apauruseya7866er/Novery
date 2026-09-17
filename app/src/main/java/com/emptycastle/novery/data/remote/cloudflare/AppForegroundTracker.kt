package com.emptycastle.novery.data.remote.cloudflare

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

/**
 * Slice-02.2: process foreground signal for the headless solver.
 *
 * Background tasks must never pop visible UI; the solver additionally
 * avoids burning a hidden WebView while the app is in the background.
 * Registered once in NoveryApp.onCreate.
 */
object AppForegroundTracker : Application.ActivityLifecycleCallbacks {

    private val resumed = AtomicInteger(0)

    /** True when at least one activity is resumed. */
    val isForeground: Boolean
        get() = resumed.get() > 0

    override fun onActivityResumed(activity: Activity) {
        resumed.incrementAndGet()
    }

    override fun onActivityPaused(activity: Activity) {
        resumed.decrementAndGet()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
