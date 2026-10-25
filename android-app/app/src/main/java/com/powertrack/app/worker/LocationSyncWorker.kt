package com.powertrack.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.powertrack.app.data.repository.LocationRepository

class LocationSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = LocationRepository(applicationContext)

        return try {
            val uploadedCount = repository.uploadPendingLocations()
            if (uploadedCount >= 0) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 5) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
