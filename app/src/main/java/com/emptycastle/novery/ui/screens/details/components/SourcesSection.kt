package com.emptycastle.novery.ui.screens.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emptycastle.novery.data.local.entity.WorkProjectionEntity

/**
 * Slice-04.2: alternate-source hub on the details screen.
 * Visible only when the work has more than one attached projection.
 * Switch changes the default; Open navigates; Remove detaches the
 * projection (library rows, history and downloads are never deleted).
 */
@Composable
fun SourcesSection(
    projections: List<WorkProjectionEntity>,
    defaultUrl: String?,
    currentUrl: String,
    onOpen: (novelUrl: String, providerName: String) -> Unit,
    onSwitch: (novelUrl: String) -> Unit,
    onDetach: (novelUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (projections.size <= 1) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Sources (${projections.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            projections.forEach { projection ->
                SourceRow(
                    projection = projection,
                    isCurrent = projection.novelUrl == currentUrl,
                    isDefault = projection.novelUrl == defaultUrl,
                    onOpen = { onOpen(projection.novelUrl, projection.providerName) },
                    onSwitch = { onSwitch(projection.novelUrl) },
                    onDetach = { onDetach(projection.novelUrl) }
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    projection: WorkProjectionEntity,
    isCurrent: Boolean,
    isDefault: Boolean,
    onOpen: () -> Unit,
    onSwitch: () -> Unit,
    onDetach: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = projection.providerName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isCurrent) {
                    SourceBadge("Current")
                }
                if (isDefault) {
                    SourceBadge("Default")
                }
            }
            Text(
                text = projection.novelUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!isDefault) {
            TextButton(onClick = onSwitch) { Text("Switch") }
        }
        if (!isCurrent) {
            TextButton(onClick = onOpen) { Text("Open") }
        }
        TextButton(onClick = onDetach) {
            Text(
                "Remove",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SourceBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}
