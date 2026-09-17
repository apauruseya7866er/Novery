package com.emptycastle.novery.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Slice-03: stable work identity (Kototoro-style entity layer).
 *
 * One Work = one real novel in the user's library. Favorites, history,
 * statistics and tracking conceptually belong to the Work; source entries
 * ([WorkProjectionEntity]) are replaceable windows into it.
 *
 * Slice 3.1 creates the tables and backfills one Work (+ one default
 * projection) per existing library row. Nothing else moves yet.
 */
@Entity(tableName = "works")
data class WorkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Novel URL of the preferred projection (null until chosen). */
    val defaultNovelUrl: String? = null
)
