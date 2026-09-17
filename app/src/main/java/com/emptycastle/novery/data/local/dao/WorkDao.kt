package com.emptycastle.novery.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emptycastle.novery.data.local.entity.WorkEntity
import com.emptycastle.novery.data.local.entity.WorkProjectionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Slice-03: work identity + projection persistence.
 */
@Dao
interface WorkDao {

    // -- Works ----------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWork(work: WorkEntity): Long

    @Query("SELECT * FROM works WHERE id = :id")
    suspend fun getWork(id: Long): WorkEntity?

    @Query("SELECT * FROM works")
    suspend fun getAllWorks(): List<WorkEntity>

    @Query("UPDATE works SET defaultNovelUrl = :novelUrl WHERE id = :id")
    suspend fun setDefaultNovelUrl(id: Long, novelUrl: String?)

    @Query("DELETE FROM works WHERE id = :id")
    suspend fun deleteWork(id: Long)

    // -- Projections ----------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProjection(projection: WorkProjectionEntity): Long

    @Query("SELECT * FROM work_projections WHERE workId = :workId ORDER BY addedAt ASC")
    suspend fun getProjections(workId: Long): List<WorkProjectionEntity>

    @Query("SELECT * FROM work_projections WHERE novelUrl = :novelUrl")
    suspend fun getProjectionByUrl(novelUrl: String): WorkProjectionEntity?

    @Query("SELECT * FROM work_projections")
    suspend fun getAllProjections(): List<WorkProjectionEntity>

    @Query("SELECT * FROM work_projections")
    fun observeAllProjections(): Flow<List<WorkProjectionEntity>>

    @Query("SELECT COUNT(*) FROM work_projections WHERE workId = :workId")
    suspend fun projectionCount(workId: Long): Int

    @Query("DELETE FROM work_projections WHERE id = :id")
    suspend fun deleteProjection(id: Long)

    @Query("DELETE FROM work_projections")
    suspend fun deleteAllProjections()

    @Query("DELETE FROM works")
    suspend fun deleteAllWorks()
}
