package com.emptycastle.novery.data.repository

import com.emptycastle.novery.data.local.dao.LibraryDao
import com.emptycastle.novery.data.local.dao.WorkDao
import com.emptycastle.novery.data.local.entity.WorkEntity
import com.emptycastle.novery.data.local.entity.WorkProjectionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Slice-03: work identity + projection management (Kototoro-style entity
 * layer, novel-app edition).
 *
 * - One [WorkEntity] per real novel; source entries attach as projections.
 * - Favorites/history/stats stay keyed by novelUrl for now (slice 4 moves
 *   merge semantics, never silently).
 */
class WorkRepository(
    private val workDao: WorkDao,
    private val libraryDao: LibraryDao
) {

    /**
     * Idempotent backfill: every library row gets exactly one work with one
     * projection. Safe to run on every app start; only fills gaps.
     * @return number of works created.
     */
    suspend fun ensureBackfilled(): Int = withContext(Dispatchers.IO) {
        var created = 0
        for (entity in libraryDao.getAll()) {
            if (workDao.getProjectionByUrl(entity.url) != null) continue
            val workId = workDao.insertWork(WorkEntity(defaultNovelUrl = entity.url))
            workDao.insertProjection(
                WorkProjectionEntity(
                    workId = workId,
                    novelUrl = entity.url,
                    providerName = entity.apiName
                )
            )
            created++
        }
        created
    }

    suspend fun getWork(id: Long): WorkEntity? = withContext(Dispatchers.IO) {
        workDao.getWork(id)
    }

    suspend fun getProjections(workId: Long): List<WorkProjectionEntity> =
        withContext(Dispatchers.IO) {
            workDao.getProjections(workId)
        }

    suspend fun getWorkForNovel(novelUrl: String): WorkEntity? =
        withContext(Dispatchers.IO) {
            val projection = workDao.getProjectionByUrl(novelUrl) ?: return@withContext null
            workDao.getWork(projection.workId)
        }

    /**
     * Sets the preferred projection for a work. The URL must already be an
     * attached projection — never invents bindings.
     * @return false when the URL is not attached to the work.
     */
    suspend fun setDefaultProjection(workId: Long, novelUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            val projections = workDao.getProjections(workId)
            if (projections.none { it.novelUrl == novelUrl }) return@withContext false
            workDao.setDefaultNovelUrl(workId, novelUrl)
            true
        }

    /**
     * Resolves which projection to open for a work: the explicit default
     * when it is still attached, otherwise the oldest attached projection.
     */
    suspend fun resolveDefaultProjection(workId: Long): WorkProjectionEntity? =
        withContext(Dispatchers.IO) {
            val work = workDao.getWork(workId) ?: return@withContext null
            val projections = workDao.getProjections(workId)
            if (projections.isEmpty()) return@withContext null
            WorkTitles.resolveDefault(projections, work.defaultNovelUrl)
        }
}
