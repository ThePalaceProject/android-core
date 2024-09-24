package org.nypl.simplified.books.time.tracking

import android.content.Context
import io.azam.ulidj.ULID
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
import org.slf4j.MDC
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min

class TimeTrackingService(
  context: Context,
  private val httpCalls: TimeTrackingHTTPCallsType,
  private val profilesController: ProfilesControllerType,
  private val timeTrackingDirectory: File,
  private val timeTrackingDebugDirectory: File,
  private val isPlayerPlayingCheck: () -> Boolean
) : TimeTrackingServiceType {

  companion object {
    private const val FILE_NAME_TIME_ENTRIES = "time_entries.json"
    private const val FILE_NAME_TIME_ENTRIES_RETRY = "time_entries_to_retry.json"

    private const val MAX_SECONDS_PLAYED = 60
  }

  private val logger =
    LoggerFactory.getLogger(TimeTrackingServiceType::class.java)
  private val dateFormatter =
    DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm'Z'")
  private val disposables =
    CompositeDisposable()
  private val connectivityListener: TimeTrackingConnectivityListener

  @Volatile
  private var mostRecentLibraryId: String? = null

  @Volatile
  private var mostRecentBookId: String? = null

  @Volatile
  private lateinit var timeEntriesFile: File

  @Volatile
  private lateinit var timeEntriesToRetryFile: File

  @Volatile
  private var audiobookPlayingDisposable: Disposable? = null

  @Volatile
  private var currentTimeTrackingEntry: TimeTrackingEntry? = null

  @Volatile
  private var firstIterationOfService = true

  @Volatile
  private var isPlaying = false

  @Volatile
  private var isOnAudiobookScreen = false

  @Volatile
  private var shouldSaveRemotely = false

  @Volatile
  private var tracking = false

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
    this.tracking = timeTrackingUri != null
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

    TimeTrackingDebugging.onTimeTrackingStarted(
      timeTrackingDebugDirectory = timeTrackingDebugDirectory,
      libraryId = libraryId,
      bookId = bookId
    )

    this.mostRecentLibraryId = libraryId
    this.mostRecentBookId = bookId

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
    if (!this.tracking) {
      return
    }

    when (playerEvent) {
      is PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackProgressUpdate,
      is PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted -> {
        isPlaying = true
        if (audiobookPlayingDisposable == null) {
          createTimeTrackingEntry()
          startEventDisposables()
        }
      }

      is PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackBuffering,
      is PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackWaitingForAction,
      is PlayerEvent.PlayerEventWithPosition.PlayerEventChapterWaiting,
      is PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPaused,
      is PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStopped,
      is PlayerEvent.PlayerEventWithPosition.PlayerEventChapterCompleted -> {
        isPlaying = false
      }

      is PlayerEvent.PlayerEventWithPosition.PlayerEventCreateBookmark,
      is PlayerEvent.PlayerEventPlaybackRateChanged,
      is PlayerEvent.PlayerEventError,
      PlayerEvent.PlayerEventManifestUpdated -> {
        // do nothing
      }

      is PlayerEvent.PlayerAccessibilityEvent.PlayerAccessibilityChapterSelected,
      is PlayerEvent.PlayerAccessibilityEvent.PlayerAccessibilityErrorOccurred,
      is PlayerEvent.PlayerAccessibilityEvent.PlayerAccessibilityIsBuffering,
      is PlayerEvent.PlayerAccessibilityEvent.PlayerAccessibilityIsWaitingForChapter,
      is PlayerEvent.PlayerAccessibilityEvent.PlayerAccessibilityPlaybackRateChanged,
      is PlayerEvent.PlayerAccessibilityEvent.PlayerAccessibilitySleepTimerSettingChanged,
      is PlayerEvent.PlayerEventDeleteBookmark,
      is PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPreparing -> {
        // do nothing
      }
    }
  }

  override fun stopTracking() {
    if (!this.tracking) {
      return
    }

    logger.debug("Stop tracking playing time")

    TimeTrackingDebugging.onTimeTrackingStopped(
      timeTrackingDebugDirectory = timeTrackingDebugDirectory,
      libraryId = this.mostRecentLibraryId ?: "Missing library ID!",
      bookId = this.mostRecentBookId ?: "Missing book ID!"
    )

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
      id = ULID.random(),
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

  private fun saveTimeTrackingInfoRemotely(
    timeTrackingInfo: TimeTrackingInfo
  ): TimeTrackingInfo {
    val timeEntriesToSend =
      timeTrackingInfo.timeEntries.filter { timeEntry -> timeEntry.isValidTimeEntry() }

    val timeEntryMap = mutableMapOf<String, TimeTrackingEntry>()
    timeEntriesToSend.forEach { e -> timeEntryMap[e.id] = e }

    /*
     * Record the fact that we made an attempt to send each entry.
     */

    timeEntriesToSend.forEach { entry ->
      TimeTrackingDebugging.onTimeTrackingSendAttempt(
        timeTrackingDebugDirectory = this.timeTrackingDebugDirectory,
        libraryId = timeTrackingInfo.libraryId,
        bookId = timeTrackingInfo.bookId,
        entryId = entry.id,
        seconds = entry.secondsPlayed
      )
    }

    val failedEntries = try {
      httpCalls.registerTimeTrackingInfo(
        timeTrackingInfo = timeTrackingInfo.copy(
          timeEntries = timeEntriesToSend
        ),
        account = profilesController
          .profileCurrent()
          .account(AccountID(UUID.fromString(timeTrackingInfo.accountId)))
      )
    } catch (exception: Exception) {
      logger.debug("Error while saving time tracking info remotely: ", exception)

      /*
       * There was an exception sending, so we record the fact that every single entry
       * failed and include the exception for each.
       */

      timeEntriesToSend.forEach { entry ->
        TimeTrackingDebugging.onTimeTrackingSendAttemptFailedExceptionally(
          timeTrackingDebugDirectory = this.timeTrackingDebugDirectory,
          libraryId = timeTrackingInfo.libraryId,
          bookId = timeTrackingInfo.bookId,
          entryId = entry.id,
          exception = exception
        )
      }

      // in case an exception occurs, we keep the original time entries
      return timeTrackingInfo.copy(timeEntries = timeTrackingInfo.timeEntries)
    }

    /*
     * Record the fact that each failed entry has failed.
     */

    for (e in failedEntries) {
      timeEntryMap.remove(e.id)
      TimeTrackingDebugging.onTimeTrackingSendAttemptFailed(
        timeTrackingDebugDirectory = this.timeTrackingDebugDirectory,
        libraryId = timeTrackingInfo.libraryId,
        bookId = timeTrackingInfo.bookId,
        entryId = e.id
      )
    }

    /*
     * Every entry that didn't fail is recorded as having succeeded.
     */

    timeEntryMap.forEach { p ->
      TimeTrackingDebugging.onTimeTrackingSendAttemptSucceeded(
        timeTrackingDebugDirectory = this.timeTrackingDebugDirectory,
        libraryId = timeTrackingInfo.libraryId,
        bookId = timeTrackingInfo.bookId,
        entryId = p.value.id
      )
    }

    return timeTrackingInfo.copy(timeEntries = failedEntries)
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
              if (!this.isPlayerPlayingCheck()) {
                try {
                  MDC.put("Ticket", "PP-1736")
                  logger.warn(
                    "Time tracking service and isPlayerPlayingCheck disagree!",
                    IllegalStateException()
                  )
                } finally {
                  MDC.remove("Ticket")
                }
              }

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
            logger.debug("Error on audiobook playing timer: ", it)
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
            logger.debug("Error on remote storage timer: ", it)
          }
        )
    )
  }
}
