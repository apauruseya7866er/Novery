package com.emptycastle.novery.data.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Slice-05.4: process-wide registry of visible background work
 * (library updates, downloads). Workers/services report here; banner UI
 * observes. Same-process singleton — no IPC, no persistence.
 */
data class BackgroundWork(
    val id: String,
    val label: String,
    val detail: String? = null
)

object BackgroundWorkTracker {

    const val ID_LIBRARY_UPDATE = "library_update"

    private val _works = MutableStateFlow<Map<String, BackgroundWork>>(emptyMap())
    val works: StateFlow<Map<String, BackgroundWork>> = _works.asStateFlow()

    fun start(id: String, label: String, detail: String? = null) {
        _works.update { it + (id to BackgroundWork(id, label, detail)) }
    }

    fun update(id: String, detail: String?) {
        _works.update { current ->
            val existing = current[id] ?: return@update current
            current + (id to existing.copy(detail = detail))
        }
    }

    fun finish(id: String) {
        _works.update { it - id }
    }

    /** Test/seatbelt helper. */
    fun clear() {
        _works.value = emptyMap()
    }
}
