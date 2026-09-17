package com.emptycastle.novery.ui.screens.notification

import com.emptycastle.novery.data.repository.LibraryItem
import com.emptycastle.novery.data.repository.NotificationEntry

/**
 * Combined notification item with library data and notification state
 */
data class NotificationDisplayItem(
    val libraryItem: LibraryItem,
    val notificationEntry: NotificationEntry,
    val isNew: Boolean
)

/**
 * Slice-05.1: one date bucket of the Updates list.
 */
data class UpdateGroupSection(
    val group: UpdateGroup,
    val items: List<NotificationDisplayItem>
)

data class NotificationUiState(
    val displayItems: List<NotificationDisplayItem> = emptyList(),
    // Slice-05.1: date-grouped view of displayItems (Today/Yesterday/Earlier).
    val groupedItems: List<UpdateGroupSection> = emptyList(),
    val isLoading: Boolean = true,

    val totalNewChapters: Int = 0,
    val totalNovelsCount: Int = 0,
    val unacknowledgedCount: Int = 0,

    val isDownloadingAll: Boolean = false,
    val downloadingNovelUrls: Set<String> = emptySet(),

    val isMarkingAllSeen: Boolean = false,

    val showClearConfirmation: Boolean = false
)