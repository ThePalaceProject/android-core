package org.nypl.simplified.ui.main

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object MainSyncService {
  fun start(context: Application) {
    val hourlyWork =
      PeriodicWorkRequestBuilder<MainSyncServiceWorker>(
        1, TimeUnit.HOURS
      ).build()

    WorkManager
      .getInstance(context)
      .enqueueUniquePeriodicWork(
        "palace-periodic-book-syncing-service",
        ExistingPeriodicWorkPolicy.UPDATE,
        hourlyWork
      )
  }
}
