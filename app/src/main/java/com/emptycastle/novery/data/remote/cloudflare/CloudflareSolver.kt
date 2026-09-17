package com.emptycastle.novery.data.remote.cloudflare

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.emptycastle.novery.data.remote.CloudflareManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/**
 * Slice-02.2: headless Cloudflare clearance session.
 *
 * Loads the challenged URL in an invisible WebView so Cloudflare's
 * JavaScript runs and sets cf_clearance, then stores it in
 * [CloudflareManager] for the OkHttp stack to reuse.
 *
 * Rules (from Kototoro's solver plan, adapted):
 * - The WebView inherits the caller's User-Agent — clearance cookies are
 *   rejected when the solving UA differs from the requesting UA.
 * - One session per host at a time (per-host Mutex); hosts run in parallel.
 * - Failures/timeouts cool the host down ([SolveCooldown]) — no hot loops.
 * - Foreground only ([AppForegroundTracker]) — background never solves.
 * - Success means "caller may retry", not "request succeeded" — the retry
 *   contract lands in 2.3.
 */
object CloudflareSolver {

    private const val TAG = "CloudflareSolver"
    private const val POLL_INTERVAL_MS = 1000L

    private val hostLocks = ConcurrentHashMap<String, Mutex>()
    private val cooldown = SolveCooldown()

    /** For tests: remaining cooldown for a normalized host. */
    fun cooldownRemainingMs(host: String): Long = cooldown.remainingMs(host)

    sealed interface SolveResult {
        /** Fresh cf_clearance stored — caller should retry the request. */
        data object Cleared : SolveResult

        /** Page loaded and shows no challenge — nothing to solve. */
        data object NoChallenge : SolveResult

        /** Timed out waiting for clearance (host now cooling down). */
        data object Timeout : SolveResult

        /** Could not run the session (cooldown, background, WebView error). */
        data object Failed : SolveResult
    }

    /**
     * @param context any Context (application context is used internally).
     * @param url the challenged page URL.
     * @param userAgent the UA the failing request used — inherited by the
     * WebView so the clearance matches.
     * @param timeoutMs max time to wait for clearance.
     */
    suspend fun solve(
        context: Context,
        url: String,
        userAgent: String = CloudflareManager.WEBVIEW_USER_AGENT,
        timeoutMs: Long = 45_000L
    ): SolveResult {
        val host = CloudflareManager.getDomain(url)
        if (host.isBlank()) return SolveResult.Failed

        if (!AppForegroundTracker.isForeground) {
            Log.i(TAG, "solve($host): app in background — refusing")
            return SolveResult.Failed
        }
        if (cooldown.isCooling(host)) {
            Log.i(TAG, "solve($host): cooling down (${cooldown.remainingMs(host)}ms left)")
            return SolveResult.Failed
        }

        val lock = hostLocks.getOrPut(host) { Mutex() }
        return lock.withLock {
            runSession(context.applicationContext, url, host, userAgent, timeoutMs)
        }
    }

    private suspend fun runSession(
        appContext: Context,
        url: String,
        host: String,
        userAgent: String,
        timeoutMs: Long
    ): SolveResult {
        return try {
            withTimeoutOrNull(timeoutMs) {
                runHeadless(appContext, url, host, userAgent)
            } ?: run {
                Log.w(TAG, "solve($host): timed out after ${timeoutMs}ms")
                cooldown.recordFailure(host)
                SolveResult.Timeout
            }
        } catch (e: Exception) {
            Log.e(TAG, "solve($host): session failed", e)
            cooldown.recordFailure(host)
            SolveResult.Failed
        }
    }

    private suspend fun runHeadless(
        appContext: Context,
        url: String,
        host: String,
        userAgent: String
    ): SolveResult = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        try {
            // Already cleared (e.g. solved elsewhere)? Don't spin a session.
            if (CloudflareManager.hasClearanceCookie(url)) {
                Log.i(TAG, "solve($host): clearance already stored")
                return@withContext SolveResult.Cleared
            }

            webView = createWebView(appContext, userAgent)
            pollForClearance(webView, url, host, userAgent)
        } finally {
            try {
                webView?.stopLoading()
                webView?.removeAllViews()
                webView?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "solve($host): WebView destroy failed", e)
            }
        }
    }

    /**
     * Loads [url] in [webView] and polls for clearance. Returns Cleared as
     * soon as a valid cf_clearance appears, NoChallenge when the finished
     * page is clean, and never returns normally otherwise (the caller
     * applies the timeout).
     */
    private suspend fun pollForClearance(
        webView: WebView,
        url: String,
        host: String,
        userAgent: String
    ): SolveResult {
        var pageDone = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                super.onPageFinished(view, finishedUrl)
                pageDone = true
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.w(TAG, "solve($host): WebView error $errorCode on $failingUrl")
            }
        }

        // Seed stored cookies first — they may still be accepted.
        CloudflareManager.injectCookiesBeforeLoad(webView, url)
        webView.loadUrl(url)

        // Poll: clearance cookie wins immediately; a clean finished page
        // with no challenge markers means there was nothing to solve.
        var cleanPolls = 0
        while (true) {
            delay(POLL_INTERVAL_MS)
            coroutineContext.ensureActive()

            val cookies = CloudflareManager.extractCookiesFromWebView(url)
            if (!cookies.isNullOrBlank() &&
                CloudflareManager.isValidCloudflareCookie(cookies)
            ) {
                CloudflareManager.saveCookiesForDomain(host, cookies, userAgent)
                CloudflareManager.flushWebViewCookies()
                cooldown.recordSuccess(host)
                Log.i(TAG, "solve($host): clearance obtained")
                return SolveResult.Cleared
            }

            if (pageDone) {
                if (!pageLooksChallenged(webView)) {
                    cleanPolls++
                    // Two consecutive clean polls: real page, no challenge.
                    if (cleanPolls >= 2) {
                        Log.i(TAG, "solve($host): page clean, no challenge")
                        return SolveResult.NoChallenge
                    }
                } else {
                    cleanPolls = 0
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(appContext: Context, userAgent: String): WebView {
        // Must be created on the main thread (guaranteed by caller).
        val wv = WebView(appContext)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = userAgent
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
        }
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        return wv
    }

    private suspend fun pageLooksChallenged(webView: WebView): Boolean {
        val html = suspendCancellableCoroutine { cont ->
            try {
                webView.evaluateJavascript("document.documentElement.outerHTML.toString();") { result ->
                    if (cont.isActive) cont.resume(result ?: "")
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume("")
            }
        }
        if (html.isBlank()) return true // unseen page counts as challenged
        return CloudflareManager.isCloudflareChallengeHtml(html)
    }
}
