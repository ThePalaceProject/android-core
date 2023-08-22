package org.nypl.simplified.books.time.tracking

import android.content.Context
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.librarysimplified.audiobook.api.PlayerEvent
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory
import ulid.ULID
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlin.math.min

class TimeTrackingService(
  context: Context,
  private val httpCalls: TimeTrackingHTTPCallsType,
  private val profilesController: ProfilesControllerType,
  private val timeTrackingDirectory: File
) : TimeTrackingServiceType {

  companion object {
    private const val FILE_NAME_TIME_ENTRIES = "time_entries.json"
    private const val FILE_NAME_TIME_ENTRIES_RETRY = "time_entries_to_retry.json"

    private const val MAX_SECONDS_PLAYED = 60
  }

  private val logger = LoggerFactory.getLogger(TimeTrackingServiceType::class.java)

  private val dateFormatter =
    DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm'Z'")

  private val disposables = CompositeDisposable()

  private lateinit var timeEntriesFile: File
  private lateinit var timeEntriesToRetryFile: File

  private var audiobookPlayingDisposable: Disposable? = null
  private val connectivityListener: TimeTrackingConnectivityListener
  private var currentTimeTrackingEntry: TimeTrackingEntry? = null

  private var firstIterationOfService = true
  private var isPlaying = false
  private var isOnAudiobookScreen = false
  private var shouldSaveRemotely = false

  init {
    connectivityListener = TimeTrackingConnectivityListener(
      context = context,
      onConnectivityStateRetrieved = { hasInternet ->
        handleConnectivityState(hasInternet)
      }
    )
  }

  override fun startTimeTracking(
    accountID: AccountID,
    bookId: String,
    libraryId: String,
    timeTrackingUri: URI?
  ) {
    if (timeTrackingUri == null) {
      logger.debug(
        "Account {} and book {} has no time tracking uri",
        accountID,
        bookId
      )
      return
    }

    logger.debug(
      "Start tracking time for account {} and book {}",
      accountID.uuid.toString(),
      bookId
    )
    val libraryFile = File(timeTrackingDirectory, accountID.uuid.toString())

    // create a directory for the library
    libraryFile.mkdirs()

    val bookFile = File(libraryFile, bookId)

    // create a directory for the book inside the library
    bookFile.mkdirs()

    timeEntriesFile = File(bookFile, FILE_NAME_TIME_ENTRIES)
    timeEntriesToRetryFile = File(bookFile, FILE_NAME_TIME_ENTRIES_RETRY)

    if (!timeEntriesFile.exists()) {

      // create an entries file for this book with an initial time tracking info
      timeEntriesFile.createNewFile()

      TimeTrackingInfoFileUtils.saveTimeTrackingInfoOnFile(
        timeTrackingInfo = TimeTrackingInfo(
          accountId = accountID.uuid.toString(),
          bookId = bookId,
          libraryId = libraryId,
          timeEntries = listOf(),
          timeTrackingUri = timeTrackingUri
        ),
        file = timeEntriesFile
      )
    }

    if (!timeEntriesToRetryFile.exists()) {

      // create a file for possible entries that weren't successfully saved on the server
      timeEntriesToRetryFile.createNewFile()

      TimeTrackingInfoFileUtils.saveTimeTrackingInfoOnFile(
        timeTrackingInfo = TimeTrackingInfo(
          accountId = accountID.uuid.toString(),
          bookId = bookId,
          libraryId = libraryId,
          timeEntries = listOf(),
          timeTrackingUri = timeTrackingUri
        ),
        file = timeEntriesFile
      )
    }

    isOnAudiobookScreen = true
  }

  override fun onPlayerEventReceived(playerEvent: PlayerEvent) {
    when (playerEvent) {
      is PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackProgressUpdate,
      is PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStarted -> {
        isPlaying = true
        if (audiobookPlayingDisposable == null) {
          createTimeTrackingEntry()
          startEventDisposables()
        }
      }

      is PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackBuffering,
      is PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackWaitingForAction,
      is PlayerEvent.PlayerEventWithSpineElement.PlayerEventChapterWaiting,
      is PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackPaused,
      is PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStopped,
      is PlayerEvent.PlayerEventWithSpineElement.PlayerEventChapterCompleted -> {
        isPlaying = false
      }
      is PlayerEvent.PlayerEventWithSpineElement.PlayerEventCreateBookmark,
      is PlayerEvent.PlayerEventPlaybackRateChanged,
      is PlayerEvent.PlayerEventError,
      PlayerEvent.PlayerEventManifestUpdated -> {
        // do nothing
      }
    }
  }

  override fun stopTracking() {
    logger.debug("Stop tracking playing time")

    disposables.clear()

    // set this to null so it can work the next time
    audiobookPlayingDisposable = null

    saveTimeTrackingInfoLocally()
    currentTimeTrackingEntry = null
    firstIterationOfService = true
    shouldSaveRemotely = false
    isOnAudiobookScreen = false
  }

  private fun createTimeTrackingEntry() {
    currentTimeTrackingEntry = TimeTrackingEntry(
      id = ULID.randomULID(),
      duringMinute = dateFormatter.print(DateTime.now()),
      secondsPlayed = 0
    )
  }

  private fun getTimeTrackingInfoLocallyStored(): TimeTrackingInfo? {
    return TimeTrackingJSON.convertBytesToTimeTrackingInfo(
      bytes = timeEntriesFile.readBytes()
    )
  }

  private fun handleConnectivityState(hasInternet: Boolean) {
    // if the user is on an audiobook player screen, it means the entries will most likely be sent
    // to the server, so there's no need to do anything else
    if (isOnAudiobookScreen) {
      return
    }

    if (hasInternet) {
      saveAllLocalTimeTrackingInfoRemotely()
    }
  }

  private fun saveAllLocalTimeTrackingInfoRemotely() {
    val libraries = timeTrackingDirectory.listFiles().orEmpty()

    libraries.forEach { library ->
      val books = library.listFiles().orEmpty()

      if (books.isEmpty()) {
        library.deleteRecursively()
        return@forEach
      }

      books.forEach { book ->
        val bookFiles = book.listFiles().orEmpty()

        if (bookFiles.isNotEmpty()) {
          bookFiles.forEach { file ->
            val fileTimeTrackingInfo = TimeTrackingInfoFileUtils.getTimeTrackingInfoFromFile(
              file = file
            )

            val timeTrackingInfo = fileTimeTrackingInfo?.copy(
              timeEntries = fileTimeTrackingInfo.timeEntries.filter { timeEntry ->
                timeEntry.isValidTimeEntry()
              }
            )

            if (!timeTrackingInfo?.timeEntries.isNullOrEmpty()) {
              val updatedTimeTrackingInfo = saveTimeTrackingInfoRemotely(
                timeTrackingInfo = timeTrackingInfo!!
              )

              // we need to update the file's time tracking info with the updated info obtained from
              // the server's response
              TimeTrackingInfoFileUtils.saveTimeTrackingInfoOnFile(
                timeTrackingInfo = updatedTimeTrackingInfo,
                file = file
              )
            } else {
              file.delete()
            }
          }
        } else {
          book.deleteRecursively()
        }
      }
    }
  }

  private fun saveTimeTrackingInfoLocally() {
    val timeTrackingInfo = currentTimeTrackingEntry?.copy(
      secondsPlayed = min(currentTimeTrackingEntry!!.secondsPlayed, MAX_SECONDS_PLAYED)
    )

    if (timeTrackingInfo != null) {
      val currentBookInfo = getTimeTrackingInfoLocallyStored()

      if (currentBookInfo != null) {
        val currentEntries = currentBookInfo.timeEntries
        val updatedTimeTrackingInfo = currentBookInfo.copy(
          timeEntries = if (firstIterationOfService) {
            // if it's the first iteration of this saving method, we can add the current time tracking
            // info
            ArrayList(currentEntries).apply {
              add(timeTrackingInfo)
            }
          } else if (shouldSaveRemotely) {
            // if the info should be remotely saved, we update the last entry one last time and add a
            // new entry for future iterations
            ArrayList(currentEntries).apply {
              set(currentEntries.lastIndex, timeTrackingInfo)
              createTimeTrackingEntry()
              add(currentTimeTrackingEntry)
            }
          } else if (currentEntries.isNotEmpty()) {
            // if there's no need to save the info remotely, we just update the last index's info
            ArrayList(currentEntries).apply {
              set(currentEntries.lastIndex, timeTrackingInfo)
            }
          } else {
            // there are no current entries, so we can create a new list
            listOf(timeTrackingInfo)
          }
        )

        firstIterationOfService = false

        TimeTrackingInfoFileUtils.saveTimeTrackingInfoOnFile(
          timeTrackingInfo = updatedTimeTrackingInfo,
          file = timeEntriesFile
        )
      }
    }
  }

  private fun saveTimeTrackingInfoRemotely(timeTrackingInfo: TimeTrackingInfo): TimeTrackingInfo {
    val failedEntries = try {
      httpCalls.registerTimeTrackingInfo(
        timeTrackingInfo = timeTrackingInfo.copy(
          timeEntries = timeTrackingInfo.timeEntries.filter { timeEntry ->
            timeEntry.isValidTimeEntry()
          }
        ),
        account = profilesController
          .profileCurrent()
          .account(AccountID(UUID.fromString(timeTrackingInfo.accountId)))
      )
    } catch (exception: Exception) {

      // in case an exception occurs, we keep the original time entries
      timeTrackingInfo.timeEntries
    }

    return timeTrackingInfo.copy(
      timeEntries = failedEntries
    )
  }

  private fun startEventDisposables() {
    // this timer will be running every second and will update the time tracking entry
    // 'secondsPlayed' value accordingly
    audiobookPlayingDisposable =
      Observable.interval(1L, TimeUnit.SECONDS)
        .subscribeOn(Schedulers.io())
        .subscribe(
          {
            if (isPlaying) {
              currentTimeTrackingEntry = currentTimeTrackingEntry?.copy(
                secondsPlayed = currentTimeTrackingEntry!!.secondsPlayed + 1
              )
            }

            saveTimeTrackingInfoLocally()

            if (shouldSaveRemotely) {
              shouldSaveRemotely = false

              val localTimeTrackingInfo = getTimeTrackingInfoLocallyStored()
              val timeTrackingInfo = localTimeTrackingInfo?.copy(
                timeEntries = localTimeTrackingInfo.timeEntries.filter { timeEntry ->
                  timeEntry.isValidTimeEntry()
                }
              )

              if (!timeTrackingInfo?.timeEntries.isNullOrEmpty()) {
                val updatedTimeTrackingInfo = saveTimeTrackingInfoRemotely(
                  timeTrackingInfo = timeTrackingInfo!!
                )

                // we can 'reset' the current time entries file
                TimeTrackingInfoFileUtils.saveTimeTrackingInfoOnFile(
                  timeTrackingInfo = timeTrackingInfo.copy(
                    timeEntries = listOf()
                  ),
                  file = timeEntriesFile
                )

                // we need to add the failed entries to the 'retry' file
                TimeTrackingInfoFileUtils.addEntriesToFile(
                  entries = updatedTimeTrackingInfo.timeEntries,
                  file = timeEntriesToRetryFile
                )
              }
            }
          },
          {
            logger.error("Error on audiobook playing timer")
            it.printStackTrace()
          }
        )

    disposables.add(audiobookPlayingDisposable!!)

    disposables.add(
      // this timer will be responsible for updating the flag to save the entries on the server
      Observable.interval(1L, TimeUnit.MINUTES)
        .subscribeOn(Schedulers.io())
        .subscribe(
          {
            shouldSaveRemotely = true
          },
          {
            logger.error("Error on remote storage timer")
            it.printStackTrace()
          }
        )
    )
  }
}
