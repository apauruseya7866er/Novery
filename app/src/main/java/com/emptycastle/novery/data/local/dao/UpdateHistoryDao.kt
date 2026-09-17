package com.emptycastle.novery.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emptycastle.novery.data.local.entity.UpdateDetectionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Slice-01.4: persistence for new-chapter detection events.
 */
@Dao
interface UpdateHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(detection: UpdateDetectionEntity): Long

    @Query("SELECT detectedAt FROM update_detections WHERE novelUrl = :novelUrl ORDER BY detectedAt DESC LIMIT :limit")
    suspend fun getRecentDetections(novelUrl: String, limit: Int = 10): List<Long>

    @Query("SELECT * FROM update_detections ORDER BY detectedAt DESC LIMIT :limit")
    suspend fun getAllRecent(limit: Int = 2000): List<UpdateDetectionEntity>

    @Query("SELECT * FROM update_detections ORDER BY detectedAt DESC LIMIT :limit")
    fun observeAllRecent(limit: Int = 2000): Flow<List<UpdateDetectionEntity>>

    @Query(
        "DELETE FROM update_detections WHERE novelUrl = :novelUrl AND id NOT IN " +
            "(SELECT id FROM update_detections WHERE novelUrl = :novelUrl " +
            "ORDER BY detectedAt DESC LIMIT :keep)"
    )
    suspend fun prune(novelUrl: String, keep: Int = 10)
}
