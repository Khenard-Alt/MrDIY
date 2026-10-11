package com.mrdiy.careers.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Runs in the background (about every 15 minutes) so notifications arrive even when the app is closed. */
class AlertCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            AlertNotifier.checkAndNotify(applicationContext)
            Result.success()
        } catch (e: Exception) {
            // Network hiccups are normal in the background; try again next period.
            Result.success()
        }
    }
}
