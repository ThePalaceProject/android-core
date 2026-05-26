package org.nypl.simplified.ui.main

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.common.util.concurrent.MoreExecutors
import org.librarysimplified.services.api.Services
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory

class MainSyncServiceWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {

  companion object {
    private val logger =
      LoggerFactory.getLogger(MainSyncServiceWorker::class.java)
  }

  override suspend fun doWork(): Result {
    try {
      logger.debug("Syncing")

      Services.serviceDirectoryFuture()
        .addListener(
          this::runTask,
          MoreExecutors.directExecutor()
        )
    } catch (e: Throwable) {
      logger.debug("Book syncing failed: ", e)
    }

    // We don't care if syncing fails.
    return Result.success()
  }

  private fun runTask() {
    logger.debug("Service directory is ready.")

    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    val books =
      services.requireService(BooksControllerType::class.java)

    val accounts =
      profiles.profileCurrent()
        .accounts()
        .values

    for (account in accounts) {
      books.booksSync(account.id)
    }
  }
}
