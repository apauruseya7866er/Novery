package com.emptycastle.novery.ui.screens.home.tabs.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emptycastle.novery.data.feed.SavedSearch
import com.emptycastle.novery.domain.model.Novel
import com.emptycastle.novery.ui.components.NovelCard

/**
 * Slice-07.1a: Latest feed — newest listings from every enabled source.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedTab(
    onNavigateToDetails: (novelUrl: String, providerName: String) -> Unit,
    viewModel: FeedViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedSearches by viewModel.savedSearches.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Feed", fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh feed")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading && uiState.sections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 70.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Slice-07.1b: search + saved searches.
            item(key = "feed_search") {
                FeedSearchBar(
                    query = uiState.searchQuery,
                    isSearching = uiState.isSearching,
                    canSave = uiState.hasSearched && uiState.searchSections.any { it.novels.isNotEmpty() },
                    onQueryChange = viewModel::updateQuery,
                    onSearch = { viewModel.runSearch() },
                    onClear = { viewModel.clearSearch() },
                    onSave = { viewModel.saveSearch() }
                )
            }

            if (savedSearches.isNotEmpty()) {
                item(key = "feed_chips") {
                    SavedSearchChips(
                        saved = savedSearches,
                        onRun = { viewModel.runSearch(it.query) },
                        onDelete = { viewModel.deleteSavedSearch(it.id) }
                    )
                }
            }

            if (uiState.hasSearched) {
                uiState.searchSections.forEach { section ->
                    item(key = "search_header_${section.providerName}") {
                        FeedSectionHeader(
                            providerName = section.providerName,
                            count = section.novels.size,
                            error = section.error
                        )
                    }
                    if (section.novels.isNotEmpty()) {
                        item(key = "search_row_${section.providerName}") {
                            FeedNovelRow(
                                novels = section.novels,
                                onNovelClick = onNavigateToDetails
                            )
                        }
                    }
                }
                if (!uiState.isSearching && uiState.searchSections.all { it.novels.isEmpty() }) {
                    item(key = "search_empty") {
                        FeedEmptyState(text = "No results — try another title")
                    }
                }
            } else {
                uiState.sections.forEach { section ->
                    item(key = "feed_header_${section.providerName}") {
                        FeedSectionHeader(
                            providerName = section.providerName,
                            count = section.novels.size,
                            error = section.error
                        )
                    }
                    if (section.novels.isNotEmpty()) {
                        item(key = "feed_row_${section.providerName}") {
                            FeedNovelRow(
                                novels = section.novels,
                                onNovelClick = onNavigateToDetails
                            )
                        }
                    }
                }

                if (uiState.sections.isEmpty()) {
                    item(key = "feed_empty") {
                        FeedEmptyState(text = "No sources enabled")
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedSectionHeader(
    providerName: String,
    count: Int,
    error: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = providerName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (error != null) "failed" else "$count new",
                style = MaterialTheme.typography.labelMedium,
                color = if (error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FeedNovelRow(
    novels: List<Novel>,
    onNovelClick: (novelUrl: String, providerName: String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = novels,
            key = { it.url }
        ) { novel ->
            FeedNovelCard(
                novel = novel,
                onClick = {
                    onNovelClick(novel.url, novel.apiName)
                }
            )
        }
    }
}

@Composable
private fun FeedEmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FeedNovelCard(
    novel: Novel,
    onClick: () -> Unit
) {
    NovelCard(
        novel = novel,
        onClick = onClick,
        modifier = Modifier.width(140.dp)
    )
}

@Composable
private fun FeedSearchBar(
    query: String,
    isSearching: Boolean,
    canSave: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search all sources") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    }
                },
                modifier = Modifier.weight(1f)
            )
            if (canSave) {
                TextButton(onClick = onSave) { Text("Save") }
            }
        }
        if (isSearching) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SavedSearchChips(
    saved: List<SavedSearch>,
    onRun: (SavedSearch) -> Unit,
    onDelete: (SavedSearch) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = saved,
            key = { it.id }
        ) { item ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clip(RoundedCornerShape(16.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onRun(item) }
                ) {
                    Text(
                        text = item.query,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                    )
                    TextButton(onClick = { onDelete(item) }) {
                        Text("×")
                    }
                }
            }
        }
    }
}
