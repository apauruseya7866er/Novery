package com.emptycastle.novery.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Slice-05.2: novel deep links from system notifications.
 *
 * MainActivity posts here from notification intents (onCreate + onNewIntent);
 * the composition consumes the link by navigating to details, then clears it.
 */
object NovelDeepLink {

    data class Target(val novelUrl: String, val providerName: String)

    private val _link = MutableStateFlow<Target?>(null)
    val link: StateFlow<Target?> = _link.asStateFlow()

    fun post(novelUrl: String, providerName: String) {
        if (novelUrl.isBlank() || providerName.isBlank()) return
        _link.value = Target(novelUrl, providerName)
    }

    fun consume() {
        _link.value = null
    }
}
