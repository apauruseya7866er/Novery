package com.emptycastle.novery.ui.screens.home.tabs.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emptycastle.novery.data.feed.FeedEngine
import com.emptycastle.novery.data.feed.SavedSearch
import com.emptycastle.novery.data.feed.SavedSearches
import com.emptycastle.novery.data.repository.RepositoryProvider
import com.emptycastle.novery.domain.model.Novel
import com.emptycastle.novery.provider.MainProvider
import kotlinx.coroutines.Job
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
    val lastUpdatedAt: Long? = null,
    // Slice-07.1b: cross-provider search + saved searches.
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val searchSections: List<FeedSection> = emptyList()
)

class FeedViewModel : ViewModel() {

    private val novelRepository = RepositoryProvider.getNovelRepository()
    private val preferencesManager = RepositoryProvider.getPreferencesManager()

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    val savedSearches: StateFlow<List<SavedSearch>> = preferencesManager.savedSearches

    private var searchJob: Job? = null

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

    // ================================================================
    // SLICE-07.1b: SEARCH + SAVED SEARCHES
    // ================================================================

    fun updateQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun runSearch(query: String = _uiState.value.searchQuery) {
        val normalized = SavedSearches.normalize(query) ?: return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    searchQuery = normalized,
                    isSearching = true,
                    hasSearched = true,
                    searchSections = emptyList()
                )
            }
            try {
                val accumulator = mutableMapOf<String, FeedSection>()
                novelRepository.searchAllStreaming(normalized).collect { (providerName, result) ->
                    val section = result.fold(
                        onSuccess = { novels ->
                            FeedSection(
                                providerName = providerName,
                                novels = novels.take(FeedEngine.SECTION_LIMIT),
                                error = null
                            )
                        },
                        onFailure = { e ->
                            FeedSection(
                                providerName = providerName,
                                novels = emptyList(),
                                error = e.message?.take(120) ?: "Search failed"
                            )
                        }
                    )
                    accumulator[providerName] = section
                    _uiState.update {
                        it.copy(searchSections = accumulator.values.toList())
                    }
                }
            } finally {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                isSearching = false,
                hasSearched = false,
                searchSections = emptyList()
            )
        }
    }

    fun saveSearch(query: String = _uiState.value.searchQuery) {
        preferencesManager.addSavedSearch(query)
    }

    fun deleteSavedSearch(id: String) {
        preferencesManager.removeSavedSearch(id)
    }
}
