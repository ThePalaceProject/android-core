package org.nypl.simplified.books.borrowing

import android.app.Application
import one.irradia.mime.api.MIMEType
import org.joda.time.Instant
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.services.api.ServiceDirectoryType
import org.nypl.drm.core.AdobeAdeptExecutorType
import org.nypl.drm.core.BoundlessServiceType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.audio.AudioBookManifestStrategiesType
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryType
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.accountsDatabaseException
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.bookDatabaseFailed
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.noSubtaskAvailable
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.noSupportedAcquisitions
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.profileNotFound
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.subtaskFailed
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.unexpectedException
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowReachedLoanLimit
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskCancelled
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskHaltedEarly
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskFactoryType
import org.nypl.simplified.books.bundled.api.BundledContentResolverType
import org.nypl.simplified.content.api.ContentResolverType
import org.nypl.simplified.links.Link
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionPath
import org.nypl.simplified.opds.core.OPDSAcquisitionPathElement
import org.nypl.simplified.opds.core.getOrNull
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.taskrecorder.api.TaskStepResolution.TaskStepFailed
import org.nypl.simplified.taskrecorder.api.TaskStepResolution.TaskStepSucceeded
import org.readium.r2.lcp.LcpService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The primary borrow task implementation.
 */

class BorrowTask private constructor(
  private val requirements: BorrowRequirements,
  private val request: BorrowRequest
) : BorrowTaskType {

  companion object : BorrowTaskFactoryType {
    override fun createBorrowTask(
      requirements: BorrowRequirements,
      request: BorrowRequest
    ): BorrowTaskType {
      return BorrowTask(
        requirements = requirements,
        request = request
      )
    }
  }

  private val logger =
    LoggerFactory.getLogger(BorrowTask::class.java)

  private val cancelled =
    AtomicBoolean(false)

  private val bookId by lazy {
    BookIDs.newFromOPDSEntry(this.request.opdsAcquisitionFeedEntry)
  }

  private val bookIdBrief by lazy {
    bookId.brief()
  }

  private var databaseEntry: BookDatabaseEntryType? = null
  private lateinit var account: AccountType
  private lateinit var taskRecorder: TaskRecorderType

  private class BorrowFailedHandled(exception: Throwable?) : Exception(exception)

  private fun debug(message: String, vararg arguments: Any?) =
    this.logger.debug("[{}] $message", this.bookIdBrief, *arguments)

  private fun error(message: String, vararg arguments: Any?) =
    this.logger.error("[{}] $message", this.bookIdBrief, *arguments)

  private fun warn(message: String, vararg arguments: Any?) =
    this.logger.warn("[{}] $message", this.bookIdBrief, *arguments)

  override fun execute(): TaskResult<*> {
    this.taskRecorder = TaskRecorder.create()
    this.debug("starting")

    return try {
      return when (val start = this.request) {
        is BorrowRequest.Start -> this.executeStart(start)
      }
    } catch (e: BorrowFailedHandled) {
      this.warn("handled: ", e)
      this.taskRecorder.finishFailure<Unit>()
    } catch (e: Throwable) {
      this.error("Unhandled exception during borrowing: ", e)
      this.taskRecorder.currentStepFailedAppending(
        message = this.messageOrName(e),
        errorCode = unexpectedException,
        exception = e,
        extraMessages = listOf()
      )
      this.taskRecorder.finishFailure<Unit>()
    }
  }

  private fun publishRequestingDownload(bookID: BookID) {
    this.requirements.bookRegistry.bookOrNull(bookID)?.let { bookWithStatus ->
      this.requirements.bookRegistry.update(
        BookWithStatus(
          book = bookWithStatus.book,
          status = BookStatus.RequestingDownload(bookID)
        )
      )
    }
  }

  override fun cancel() {
    this.cancelled.set(true)
  }

  private fun messageOrName(e: Throwable) =
    e.message ?: e.javaClass.name

  private fun executeStart(start: BorrowRequest.Start): TaskResult<*> {
    this.taskRecorder.addAttribute("Book", start.opdsAcquisitionFeedEntry.title)
    this.taskRecorder.addAttribute("Author", start.opdsAcquisitionFeedEntry.authorsCommaSeparated)
    this.taskRecorder.addAttribute("Profile ID", start.profileId.toString())

    this.publishRequestingDownload(this.bookId)

    /*
     * The initial book value. Note that this is a synthesized value because we need to be
     * able to open the book database to get a real book value, and that database call might
     * fail. If the call fails, we have no "book" that we can refer to in order to publish a
     * "book download has failed" status for the book, so we use this fake book in that (rare)
     * situation.
     */

    val bookInitial =
      Book(
        id = bookId,
        account = start.accountId,
        cover = null,
        thumbnail = null,
        entry = start.opdsAcquisitionFeedEntry,
        formats = listOf()
      )

    val profile = this.findProfile(start.profileId, bookInitial)
    this.account = this.findAccount(profile, bookInitial)
    val book = this.createBookDatabaseEntry(bookInitial, start.opdsAcquisitionFeedEntry)
    val path = this.pickAcquisitionPath(book, start.opdsAcquisitionFeedEntry)
    this.executeSubtasksForPath(book, path, start.samlDownloadContext)
    return this.taskRecorder.finishSuccess(Unit)
  }

  /**
   * Execute all subtasks for the given acquisition path.
   */

  private fun executeSubtasksForPath(
    book: Book,
    path: OPDSAcquisitionPath,
    samlDownloadContext: SAMLDownloadContext?
  ) {
    val context =
      BorrowContext(
        borrowTask = this,
        application = this.requirements.application,
        account = this.account,
        adobeExecutor = this.requirements.adobeExecutor,
        boundlessService = this.requirements.boundlessService,
        lcpService = this.requirements.lcpService,
        audioBookManifestStrategies = this.requirements.audioBookManifestStrategies,
        bookDatabaseEntry = this.databaseEntry!!,
        bookInitial = book,
        bookRegistry = this.requirements.bookRegistry,
        bundledContent = this.requirements.bundledContent,
        cacheDirectory = this.requirements.cacheDirectory,
        cancelled = this.cancelled,
        clock = this.requirements.clock,
        contentResolver = this.requirements.contentResolver,
        currentOPDSAcquisitionPathElement = path.elements.first(),
        httpClient = this.requirements.httpClient,
        isManualLCPPassphraseEnabled =
        this.requirements.profiles.currentProfile().getOrNull()
          ?.preferences()?.isManualLCPPassphraseEnabled ?: false,
        logger = this.logger,
        opdsAcquisitionPath = path,
        samlDownloadContext = samlDownloadContext,
        services = this.requirements.services,
        taskRecorder = this.taskRecorder,
        temporaryDirectory = this.requirements.temporaryDirectory
      )

    while (true) {
      try {
        if (context.opdsAcquisitionPath.elements.isEmpty()) {
          break
        }

        val pathElement = context.opdsAcquisitionPath.elements[0]
        context.currentOPDSAcquisitionPathElement = pathElement

        val queue: MutableList<OPDSAcquisitionPathElement> = context.opdsAcquisitionPath.elements.toMutableList()
        queue.removeAt(0)
        context.opdsAcquisitionPath = context.opdsAcquisitionPath.copy(elements = queue.toList())
        context.currentRemainingOPDSPathElements = context.opdsAcquisitionPath.elements

        val subtaskFactory =
          this.subtaskFindForPathElement(
            context = context,
            pathElement = pathElement,
            book = book,
            remaining = queue.toList().map(OPDSAcquisitionPathElement::mimeType)
          )
        this.subtaskExecute(subtaskFactory, context, book)
      } catch (e: BorrowSubtaskHaltedEarly) {
        this.logger.debug("subtask halted early: ", e)
        return
      } catch (e: BorrowSubtaskCancelled) {
        this.logger.debug("subtask cancelled: ", e)
        return
      }
    }
  }

  /**
   * Create and execute the given subtask.
   */

  private fun subtaskExecute(
    subtaskFactory: BorrowSubtaskFactoryType,
    context: BorrowContext,
    book: Book
  ) {
    val name = subtaskFactory.name
    val step = this.taskRecorder.beginNewStep("Executing subtask '$name'...")
    try {
      subtaskFactory.createSubtask().execute(context)
      step.resolution = TaskStepSucceeded("Executed subtask '$name' successfully.")
    } catch (e: BorrowSubtaskHaltedEarly) {
      throw e
    } catch (e: BorrowSubtaskCancelled) {
      throw e
    } catch (e: BorrowReachedLoanLimit) {
      step.resolution = TaskStepFailed(
        message = "Subtask '$name' raised an unexpected exception",
        exception = e,
        errorCode = subtaskFailed,
        extraMessages = listOf()
      )
      throw e
    } catch (e: Exception) {
      step.resolution = TaskStepFailed(
        message = "Subtask '$name' raised an unexpected exception",
        exception = e,
        errorCode = subtaskFailed,
        extraMessages = listOf()
      )
      this.publishBookFailure(book)
      throw BorrowFailedHandled(e)
    }
  }

  /**
   * Find a suitable subtask for the acquisition element.
   */

  private fun subtaskFindForPathElement(
    context: BorrowContext,
    pathElement: OPDSAcquisitionPathElement,
    book: Book,
    remaining: List<MIMEType>
  ): BorrowSubtaskFactoryType {
    this.taskRecorder.beginNewStep("Finding subtask for acquisition path element ${pathElement.mimeType}...")
    val subtaskFactory =
      this.requirements.subtasks.findSubtaskFor(
        mimeType = pathElement.mimeType,
        target = context.currentURI(),
        account = context.account,
        remainingTypes = remaining
      )
    if (subtaskFactory == null) {
      this.taskRecorder.currentStepFailed(
        message = "We don't know how to handle this kind of acquisition.",
        errorCode = noSubtaskAvailable,
        extraMessages = listOf()
      )
      this.publishBookFailure(book)
      throw BorrowFailedHandled(null)
    }
    val name = subtaskFactory.name
    this.taskRecorder.currentStepSucceeded("Found subtask '$name'")
    return subtaskFactory
  }

  /**
   * Create a new book database entry and publish the status of the book.
   */

  private fun createBookDatabaseEntry(
    book: Book,
    entry: OPDSAcquisitionFeedEntry
  ): Book {
    this.taskRecorder.beginNewStep("Setting up a book database entry...")

    try {
      val database = this.account.bookDatabase
      val dbEntry = database.createOrUpdate(book.id, entry)
      this.databaseEntry = dbEntry
      this.taskRecorder.currentStepSucceeded("Book database updated.")
      return dbEntry.book
    } catch (e: Exception) {
      this.error("[{}]: failed to set up book database: ", book.id.brief(), e)
      this.taskRecorder.currentStepFailed(
        message = "Could not set up the book database entry.",
        errorCode = bookDatabaseFailed,
        exception = e,
        extraMessages = listOf()
      )
      this.publishBookFailure(book)
      throw BorrowFailedHandled(e)
    }
  }

  /**
   * Locate the given profile.
   */

  private fun findProfile(
    profileID: ProfileID,
    book: Book
  ): ProfileReadableType {
    this.taskRecorder.beginNewStep("Locating profile $profileID...")

    val profile = this.requirements.profiles.profiles()[profileID]
    return if (profile == null) {
      this.error("[{}]: failed to find profile: ", profileID)
      this.taskRecorder.currentStepFailed(
        message = "Failed to find profile.",
        errorCode = profileNotFound,
        exception = IllegalArgumentException(),
        extraMessages = listOf()
      )
      this.publishBookFailure(book)
      throw BorrowFailedHandled(null)
    } else {
      this.taskRecorder.currentStepSucceeded("Located profile.")
      profile
    }
  }

  /**
   * Locate the account in the current profile.
   */

  private fun findAccount(
    profile: ProfileReadableType,
    book: Book
  ): AccountType {
    this.taskRecorder.beginNewStep("Locating account ${book.account.uuid} in the profile...")
    this.taskRecorder.addAttribute("Account ID", book.account.uuid.toString())

    val account = try {
      profile.account(this.request.accountId)
    } catch (e: Throwable) {
      this.error("[{}]: failed to find account: ", book.id.brief(), e)
      this.taskRecorder.currentStepFailedAppending(
        message = "An unexpected exception was raised.",
        errorCode = accountsDatabaseException,
        exception = e,
        extraMessages = listOf()
      )

      this.publishBookFailure(book)
      throw BorrowFailedHandled(e)
    }

    this.taskRecorder.addAttribute("Account", account.provider.displayName)
    this.taskRecorder.currentStepSucceeded("Located account.")
    return account
  }

  /**
   * Pick the best available acquisition path.
   */

  private fun pickAcquisitionPath(
    book: Book,
    entry: OPDSAcquisitionFeedEntry
  ): OPDSAcquisitionPath {
    this.taskRecorder.beginNewStep("Planning the borrow operation…")

    val path =
      BorrowAcquisitions.pickBestAcquisitionPath(this.requirements.bookFormatSupport, entry)
    if (path == null) {
      this.taskRecorder.currentStepFailed(
        message = "No supported acquisitions.",
        errorCode = noSupportedAcquisitions,
        extraMessages = listOf()
      )
      this.publishBookFailure(book)
      throw BorrowFailedHandled(null)
    }

    this.taskRecorder.currentStepSucceeded("Selected an acquisition path.")
    return path
  }

  private fun publishBookFailure(book: Book) {
    val failure = this.taskRecorder.finishFailure<Unit>()
    this.requirements.bookRegistry.update(
      BookWithStatus(
        book,
        BookStatus.FailedLoan(book.id, failure)
      )
    )
  }

  private class BorrowContext(
    private val borrowTask: BorrowTask,
    override val application: Application,
    override val account: AccountType,
    override val audioBookManifestStrategies: AudioBookManifestStrategiesType,
    override val clock: () -> Instant,
    override val contentResolver: ContentResolverType,
    override val bundledContent: BundledContentResolverType,
    override val bookDatabaseEntry: BookDatabaseEntryType,
    override val httpClient: LSHTTPClientType,
    override val isManualLCPPassphraseEnabled: Boolean,
    override val taskRecorder: TaskRecorderType,
    @Volatile
    override var opdsAcquisitionPath: OPDSAcquisitionPath,
    override val samlDownloadContext: SAMLDownloadContext? = null,
    bookInitial: Book,
    private val bookRegistry: BookRegistryType,
    private val logger: Logger,
    private val temporaryDirectory: File,
    var currentOPDSAcquisitionPathElement: OPDSAcquisitionPathElement,
    override val adobeExecutor: AdobeAdeptExecutorType?,
    override val boundlessService: BoundlessServiceType?,
    override val lcpService: LcpService?,
    override val services: ServiceDirectoryType,
    private val cacheDirectory: File,
    private val cancelled: AtomicBoolean
  ) : BorrowContextType {

    override fun cacheDirectory(): File =
      this.cacheDirectory

    override val adobeExecutorTimeout: BorrowTimeoutConfiguration =
      BorrowTimeoutConfiguration(300L, TimeUnit.SECONDS)

    override var isCancelled
      get() = this.cancelled.get()
      set(value) = this.cancelled.set(value)

    var currentRemainingOPDSPathElements =
      this.opdsAcquisitionPath.elements

    override val bookCurrent: Book
      get() = this.bookDatabaseEntry.book

    override fun bookDownloadIsWaitingForExternalAuthentication() {
      this.bookPublishStatus(
        BookStatus.DownloadWaitingForExternalAuthentication(
          id = this.bookCurrent.id,
          downloadURI = this.currentURICheck()
        )
      )
    }

    override fun bookDownloadIsRunning(
      message: String,
      receivedSize: Long?,
      expectedSize: Long?,
      bytesPerSecond: Long?
    ) {
      this.logDebug("downloading: {} {} {}", expectedSize, receivedSize, bytesPerSecond)

      this.bookPublishStatus(
        BookStatus.Downloading(
          id = this.bookCurrent.id,
          currentTotalBytes = receivedSize,
          expectedTotalBytes = expectedSize,
          detailMessage = message
        )
      )
    }

    override fun bookPublishStatus(status: BookStatus) {
      this.bookRegistry.update(BookWithStatus(this.bookDatabaseEntry.book, status))
    }

    override fun bookDownloadSucceeded() {
      this.bookPublishStatus(BookStatus.fromBook(this.bookDatabaseEntry.book))
    }

    override fun bookLoanIsRequesting(message: String) {
      this.bookPublishStatus(
        BookStatus.RequestingLoan(
          id = this.bookCurrent.id,
          detailMessage = message
        )
      )
    }

    override fun bookLoanFailed() {
      this.bookPublishStatus(
        BookStatus.FailedLoan(
          id = this.bookCurrent.id,
          result = this.taskRecorder.finishFailure()
        )
      )
    }

    override fun bookReachedLoanLimit() {
      this.bookPublishStatus(
        BookStatus.ReachedLoanLimit(
          id = this.bookCurrent.id,
          result = this.taskRecorder.finishFailure()
        )
      )
    }

    override fun chooseNewAcquisitionPath(
      entry: OPDSAcquisitionFeedEntry
    ): Link {
      val path = this.borrowTask.pickAcquisitionPath(this.bookCurrent, entry)
      this.logDebug("Selected a new acquisition path.")
      check(path.elements.isNotEmpty()) { "Selected acquisition path cannot be empty!" }
      this.logDebug(
        "Path now starts with {} (type {})",
        path.elements[0].target,
        path.elements[0].mimeType
      )
      this.opdsAcquisitionPath = path
      this.currentRemainingOPDSPathElements = this.opdsAcquisitionPath.elements

      val target = path.elements[0].target
      check(target != null) { "Chosen path must start with a usable URI!" }
      return target
    }

    override fun bookDownloadFailed() {
      this.bookPublishStatus(
        BookStatus.FailedDownload(
          id = this.bookCurrent.id,
          result = this.taskRecorder.finishFailure()
        )
      )
    }

    var currentURIField: Link? =
      null

    override fun currentURI(): Link? {
      return this.currentURIField ?: return this.currentAcquisitionPathElement.target
    }

    override fun receivedNewURI(uri: Link) {
      this.logDebug("received new URI: {}", uri)
      this.currentURIField = uri
    }

    override val currentAcquisitionPathElement: OPDSAcquisitionPathElement
      get() = this.currentOPDSAcquisitionPathElement

    private val bookIdBrief =
      bookInitial.id.brief()

    override fun logDebug(message: String, vararg arguments: Any?) =
      this.logger.debug("[{}] $message", this.bookIdBrief, *arguments)

    override fun logError(message: String, vararg arguments: Any?) =
      this.logger.error("[{}] $message", this.bookIdBrief, *arguments)

    override fun logWarn(message: String, vararg arguments: Any?) =
      this.logger.warn("[{}] $message", this.bookIdBrief, *arguments)

    override fun temporaryFile(
      extension: String
    ): File {
      val ext = if (extension.length > 0) ".$extension" else ""

      this.temporaryDirectory.mkdirs()
      for (i in 0..100) {
        val file = File(this.temporaryDirectory, "${UUID.randomUUID()}$ext")
        if (!file.exists()) {
          return file
        }
      }
      throw IOException("Could not create a temporary file within 100 attempts!")
    }

    override fun opdsAcquisitionPathRemaining(): List<OPDSAcquisitionPathElement> {
      return this.currentRemainingOPDSPathElements
    }
  }
}
