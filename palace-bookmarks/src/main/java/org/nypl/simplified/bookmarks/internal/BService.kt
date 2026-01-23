package org.nypl.simplified.bookmarks.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.Subject
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventDeletion
import org.nypl.simplified.accounts.api.AccountEventLoginStateChanged
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedInStaleCredentials
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingInWaitingForExternalAuthentication
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingOut
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginFailed
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLogoutFailed
import org.nypl.simplified.accounts.api.AccountLoginState.AccountNotLoggedIn
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.bookmarks.api.BookmarksForBook
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfileNoneCurrentException
import org.nypl.simplified.profiles.api.ProfileSelection
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class BService(
  private val threads: (Runnable) -> Thread,
  private val httpCalls: BookmarkHTTPCallsType,
  private val bookmarkEventsOut: Subject<BookmarkEvent>,
  private val profilesController: ProfilesControllerType
) : BookmarkServiceType {

  private val disposables =
    CompositeDisposable()

  private val executor: ScheduledExecutorService =
    Executors.newScheduledThreadPool(1) { runnable ->
      BServiceThread(this.threads.invoke(runnable))
    }

  private val logger =
    LoggerFactory.getLogger(BService::class.java)
  private val objectMapper =
    ObjectMapper()

  private val attributes =
    Attributes.create { e -> this.logger.debug("Attribute error: ", e) }

  private val bookmarksSource: AttributeType<Map<AccountID, Map<BookID, BookmarksForBook>>> =
    this.attributes.withValue(mapOf())

  init {
    this.disposables.add(
      this.profilesController.profileEvents()
        .subscribe(this::onProfileEvent)
    )
    this.disposables.add(
      this.profilesController.accountEvents()
        .subscribe(this::onAccountEvent)
    )

    /*
     * Sync bookmarks hourly.
     */

    this.executor.scheduleAtFixedRate(
      { this.onRegularSyncTimeElapsed() },
      0L,
      1L,
      TimeUnit.HOURS
    )
  }

  private fun onProfileEvent(event: ProfileEvent) {
    if (event is ProfileSelection.ProfileSelectionInProgress) {
      try {
        val currentProfile = this.profilesController.profileCurrent()
        this.logger.debug("[{}]: a new profile was selected", currentProfile.id.uuid)
        this.onProfileWasSelected()
      } catch (e: ProfileNoneCurrentException) {
        this.logger.error("onProfileEvent: no profile is current")
      }
    }
  }

  private fun onAccountEvent(event: AccountEvent) {
    if (event is AccountEventDeletion.AccountEventDeletionSucceeded) {
      this.onAccountDeleted(event.id)
      return
    }

    if (event is AccountEventLoginStateChanged) {
      when (event.state) {
        is AccountLoggedIn ->
          this.onAccountLoggedIn()

        is AccountLoggedInStaleCredentials,
        is AccountLoggingIn,
        is AccountLoggingInWaitingForExternalAuthentication,
        is AccountLoggingOut,
        is AccountLoginFailed,
        is AccountLogoutFailed,
        is AccountNotLoggedIn -> {
        }
      }
    }
  }

  private fun onAccountDeleted(id: AccountID) {
    this.bookmarksSource.set(BookmarkAttributes.removeAccount(this.bookmarksSource.get(), id))
  }

  private fun <T> submitOp(
    op: BServiceOp<T>
  ): CompletableFuture<T> {
    val f = CompletableFuture<T>()
    this.executor.execute {
      try {
        f.complete(op.runActual())
      } catch (e: Throwable) {
        f.completeExceptionally(e)
      }
    }
    return f
  }

  /**
   * Asynchronously send and receive all bookmarks.
   */

  private fun sync(): CompletableFuture<*> {
    return try {
      this.submitOp(
        BServiceOpSyncAllAccounts(
          this.logger,
          this.httpCalls,
          this.bookmarkEventsOut,
          this.objectMapper,
          this.profilesController.profileCurrent(),
          this.bookmarksSource
        )
      )
    } catch (e: Throwable) {
      this.logger.debug("sync: unable to sync profile: ", e)
      this.failedFuture<Void>(e)
    }
  }

  private fun <T> failedFuture(
    e: Throwable
  ): CompletableFuture<T> {
    val f = CompletableFuture<T>()
    f.completeExceptionally(e)
    return f
  }

  private fun onAccountLoggedIn() {
    this.sync()
  }

  private fun onProfileWasSelected() {
    this.sync()
  }

  private fun onRegularSyncTimeElapsed() {
    this.sync()
  }

  override fun close() {
    this.disposables.dispose()
    this.executor.shutdown()
  }

  override val bookmarkEvents: Observable<BookmarkEvent>
    get() = this.bookmarkEventsOut

  override val bookmarks: AttributeReadableType<Map<AccountID, Map<BookID, BookmarksForBook>>>
    get() = this.bookmarksSource

  override fun bookmarkSyncAccount(
    accountID: AccountID
  ): CompletableFuture<List<SerializedBookmark>> {
    return try {
      this.submitOp(
        BServiceOpSyncOneAccount(
          this.logger,
          this.httpCalls,
          this.bookmarkEventsOut,
          this.objectMapper,
          this.profilesController.profileCurrent(),
          accountID,
          this.bookmarksSource
        )
      )
    } catch (e: Throwable) {
      this.logger.debug("sync: unable to sync account: ", e)
      this.failedFuture(e)
    }
  }

  override fun bookmarkLoadAll(): CompletableFuture<Unit> {
    return try {
      this.submitOp(
        BServiceOpLoadBookmarksForAll(
          this.logger,
          this.profilesController.profileCurrent(),
          this.bookmarksSource
        )
      )
    } catch (e: Throwable) {
      this.logger.debug("load-all: unable to load bookmarks: ", e)
      this.failedFuture(e)
    }
  }

  override fun bookmarkSyncAndLoad(
    accountID: AccountID,
    book: BookID
  ): CompletableFuture<BookmarksForBook> {
    return this.bookmarkSyncAccount(accountID)
      .exceptionally { listOf() }
      .thenCompose { this.bookmarkLoad(accountID, book) }
  }

  override fun bookmarkLoad(
    accountID: AccountID,
    book: BookID
  ): CompletableFuture<BookmarksForBook> {
    return try {
      this.submitOp(
        BServiceOpLoadBookmarksForBook(
          logger = this.logger,
          accountID = accountID,
          profile = this.profilesController.profileCurrent(),
          book = book,
          bookmarksSource = this.bookmarksSource
        )
      )
    } catch (e: Throwable) {
      this.logger.debug("bookmarkLoad: ", e)
      this.failedFuture(e)
    }
  }

  override fun bookmarkCreateLocal(
    accountID: AccountID,
    bookmark: SerializedBookmark
  ): CompletableFuture<SerializedBookmark> {
    return try {
      this.submitOp(
        BServiceOpCreateLocalBookmark(
          logger = this.logger,
          bookmarkEventsOut = this.bookmarkEventsOut,
          profile = this.profilesController.profileCurrent(),
          accountID = accountID,
          bookmark = bookmark,
          bookmarksSource = this.bookmarksSource
        )
      )
    } catch (e: Throwable) {
      this.logger.debug("bookmarkCreateLocal: ", e)
      this.failedFuture(e)
    }
  }

  override fun bookmarkCreateRemote(
    accountID: AccountID,
    bookmark: SerializedBookmark
  ): CompletableFuture<SerializedBookmark> {
    return try {
      this.submitOp(
        BServiceOpCreateRemoteBookmark(
          logger = this.logger,
          objectMapper = this.objectMapper,
          httpCalls = this.httpCalls,
          profile = this.profilesController.profileCurrent(),
          accountID = accountID,
          bookmark = bookmark,
          bookmarksSource = this.bookmarksSource
        )
      )
    } catch (e: Throwable) {
      this.logger.debug("bookmarkCreateRemote: ", e)
      this.failedFuture(e)
    }
  }

  override fun bookmarkCreate(
    accountID: AccountID,
    bookmark: SerializedBookmark,
    ignoreRemoteFailures: Boolean
  ): CompletableFuture<SerializedBookmark> {
    return try {
      this.submitOp(
        BServiceOpCreateBookmark(
          logger = this.logger,
          objectMapper = this.objectMapper,
          httpCalls = this.httpCalls,
          profile = this.profilesController.profileCurrent(),
          accountID = accountID,
          bookmarkEventsOut = this.bookmarkEventsOut,
          bookmark = bookmark,
          ignoreRemoteFailures = ignoreRemoteFailures,
          bookmarksSource = this.bookmarksSource
        )
      )
    } catch (e: Throwable) {
      this.logger.debug("bookmarkCreate: ", e)
      this.failedFuture(e)
    }
  }

  override fun bookmarkDelete(
    accountID: AccountID,
    bookmark: SerializedBookmark,
    ignoreRemoteFailures: Boolean
  ): CompletableFuture<Unit> {
    return try {
      this.submitOp(
        BServiceOpDeleteBookmark(
          logger = this.logger,
          httpCalls = this.httpCalls,
          profile = this.profilesController.profileCurrent(),
          accountID = accountID,
          bookmark = bookmark,
          ignoreRemoteFailures = ignoreRemoteFailures,
          bookmarksSource = this.bookmarksSource
        )
      )
    } catch (e: Throwable) {
      this.logger.debug("bookmarkLoad: ", e)
      this.failedFuture(e)
    }
  }
}
