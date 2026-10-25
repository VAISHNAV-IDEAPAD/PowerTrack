package com.powertrack.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.powertrack.app.data.model.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocation(location: LocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLocations(locations: List<LocationEntity>): List<Long>

    @Query("SELECT * FROM locations WHERE upload_status = :status ORDER BY recorded_at_utc ASC LIMIT :limit")
    suspend fun getPendingLocations(
        status: String = LocationEntity.STATUS_PENDING,
        limit: Int = 100
    ): List<LocationEntity>

    @Query("SELECT COUNT(*) FROM locations WHERE upload_status = :status")
    fun getPendingCountFlow(status: String = LocationEntity.STATUS_PENDING): Flow<Int>

    @Query("SELECT COUNT(*) FROM locations WHERE upload_status = :status")
    suspend fun getPendingCount(status: String = LocationEntity.STATUS_PENDING): Int

    @Query("SELECT * FROM locations ORDER BY recorded_at_utc DESC")
    fun getAllLocationsFlow(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations ORDER BY recorded_at_utc DESC LIMIT 1")
    fun getLatestLocationFlow(): Flow<LocationEntity?>

    @Query("SELECT * FROM locations ORDER BY recorded_at_utc DESC LIMIT 1")
    suspend fun getLatestLocation(): LocationEntity?

    @Query("UPDATE locations SET upload_status = :status, uploaded_at_utc = :uploadedAtUtc WHERE event_id IN (:eventIds)")
    suspend fun markAsUploaded(
        eventIds: List<String>,
        uploadedAtUtc: String,
        status: String = LocationEntity.STATUS_UPLOADED
    )

    @Query("UPDATE locations SET upload_status = :status WHERE event_id IN (:eventIds)")
    suspend fun updateUploadStatus(
        eventIds: List<String>,
        status: String
    )

    @Query("DELETE FROM locations WHERE upload_status = 'UPLOADED' AND recorded_at_utc < :cutoffUtc")
    suspend fun purgeOldUploadedLocations(cutoffUtc: String): Int

    @Query("DELETE FROM locations")
    suspend fun clearAll()
}
