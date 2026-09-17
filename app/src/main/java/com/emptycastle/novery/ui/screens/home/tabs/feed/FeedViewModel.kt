package com.emptycastle.novery.ui.screens.home.tabs.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emptycastle.novery.data.feed.FeedEngine
import com.emptycastle.novery.data.repository.RepositoryProvider
import com.emptycastle.novery.domain.model.Novel
import com.emptycastle.novery.provider.MainProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Slice-07.1a: one "latest updates" section per enabled provider.
 */
data class FeedSection(
    val providerName: String,
    val novels: List<Novel> = emptyList(),
    val error: String? = null
)

data class FeedUiState(
    val sections: List<FeedSection> = emptyList(),
    val isLoading: Boolean = true,
    val lastUpdatedAt: Long? = null
)

class FeedViewModel : ViewModel() {

    private val novelRepository = RepositoryProvider.getNovelRepository()

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // getProviders() already applies order + disabled list.
                val providers = novelRepository.getProviders()
                val sections = coroutineScope {
                    providers.map { provider ->
                        async { loadSection(provider) }
                    }.map { it.await() }
                }
                _uiState.update {
                    it.copy(
                        sections = sections,
                        isLoading = false,
                        lastUpdatedAt = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadSection(provider: MainProvider): FeedSection {
        return try {
            val orderBy = FeedEngine.latestOrderBy(provider)
            val result = provider.loadMainPage(1, orderBy)
            FeedSection(
                providerName = provider.name,
                novels = result.novels.take(FeedEngine.SECTION_LIMIT),
                error = null
            )
        } catch (e: Exception) {
            FeedSection(
                providerName = provider.name,
                novels = emptyList(),
                error = e.message?.take(120) ?: "Failed to load"
            )
        }
    }
}
