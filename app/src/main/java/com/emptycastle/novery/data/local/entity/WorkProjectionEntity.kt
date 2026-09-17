package com.emptycastle.novery.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Slice-03: a readable source entry attached to a [WorkEntity].
 *
 * A work starts with exactly one projection (its library entry); merges
 * (slice 4) attach more. The default projection is chosen via
 * [WorkEntity.defaultNovelUrl], never by duplicating state here.
 */
@Entity(
    tableName = "work_projections",
    indices = [
        Index("workId"),
        Index(value = ["novelUrl"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = WorkEntity::class,
            parentColumns = ["id"],
            childColumns = ["workId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WorkProjectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workId: Long,
    val novelUrl: String,
    val providerName: String,
    val addedAt: Long = System.currentTimeMillis()
)
