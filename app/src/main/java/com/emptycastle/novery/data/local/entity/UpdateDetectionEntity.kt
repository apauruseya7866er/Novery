package com.emptycastle.novery.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Slice-01.4: one row per background/manual refresh that detected new
 * chapters for a novel. Feeds the update-interval predictor
 * ([UpdatePredictor]); pruned to the latest rows per novel.
 */
@Entity(
    tableName = "update_detections",
    indices = [
        Index("novelUrl"),
        Index("detectedAt")
    ]
)
data class UpdateDetectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val novelUrl: String,
    val detectedAt: Long = System.currentTimeMillis(),
    val newChapters: Int = 0
)
