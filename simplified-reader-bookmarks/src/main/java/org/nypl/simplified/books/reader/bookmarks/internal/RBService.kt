package org.nypl.simplified.books.reader.bookmarks.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.util.concurrent.FluentFuture
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.Subject
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventLoginStateChanged
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingInWaitingForExternalAuthentication
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingOut
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginFailed
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLogoutFailed
import org.nypl.simplified.accounts.api.AccountLoginState.AccountNotLoggedIn
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.Bookmark
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfileNoneCurrentException
import org.nypl.simplified.profiles.api.ProfileSelection
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkEvent
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkHTTPCallsType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkServiceType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkSyncEnableResult
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkSyncEnableResult.SYNC_DISABLED
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkSyncEnableResult.SYNC_ENABLED
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkSyncEnableResult.SYNC_ENABLE_NOT_SUPPORTED
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkSyncEnableStatus
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarks
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RBService(
  private val threads: (Runnable) -> Thread,
  private val httpCalls: ReaderBookmarkHTTPCallsType,
  private val bookmarkEventsOut: Subject<ReaderBookmarkEvent>,
  private val profilesController: ProfilesControllerType
) : ReaderBookmarkServiceType {

  private val disposables =
    CompositeDisposable()

  private val executor: ListeningScheduledExecutorService =
    MoreExecutors.listeningDecorator(
      Executors.newScheduledThreadPool(1) { runnable ->
        RBServiceThread(this.threads.invoke(runnable))
      }
    )

  private val logger =
    LoggerFactory.getLogger(RBService::class.java)
  private val objectMapper =
    ObjectMapper()
  private val accountsSyncChanging =
    Collections.synchronizedSet(hashSetOf<AccountID>())

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

    this.executor.scheduleAtFixedRate({ this.onRegularSyncTimeElapsed() }, 0L, 1L, TimeUnit.HOURS)
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
    if (event is AccountEventLoginStateChanged) {
      when (event.state) {
        is AccountLoggedIn ->
          this.onAccountLoggedIn()

        is AccountLoggingIn,
        is AccountLoggingInWaitingForExternalAuthentication,
        is AccountLoggingOut,
        is AccountLoginFailed,
        is AccountLogoutFailed,
        AccountNotLoggedIn -> {
        }
      }
    }
  }

  /**
   * Asynchronously send and receive all bookmarks.
   */

  private fun sync(): ListenableFuture<*> {
    return try {
      this.executor.submit(
        RBServiceOpSyncAllAccounts(
          this.logger,
          this.httpCalls,
          this.bookmarkEventsOut,
          this.objectMapper,
          this.profilesController.profileCurrent()
        )
      )
    } catch (e: Exception) {
      this.logger.error("sync: unable to sync profile: ", e)
      FluentFuture.from(Futures.immediateFailedFuture<Unit>(e))
    }
  }

  private fun onAccountEnabledSyncing() {
    this.sync()
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

  override val bookmarkEvents: Observable<ReaderBookmarkEvent>
    get() = this.bookmarkEventsOut

  override fun bookmarkSyncStatus(
    accountID: AccountID
  ): ReaderBookmarkSyncEnableStatus {
    val profile =
      this.profilesController.profileCurrent()
    val syncable =
      RBSyncableAccount.ofAccount(profile.account(accountID))

    if (syncable == null) {
      this.logger.error("bookmarkSyncEnable: account does not support syncing")
      return ReaderBookmarkSyncEnableStatus.Idle(accountID, SYNC_ENABLE_NOT_SUPPORTED)
    }

    val changing = this.accountsSyncChanging.contains(accountID)
    if (changing) {
      return ReaderBookmarkSyncEnableStatus.Changing(accountID)
    }

    return ReaderBookmarkSyncEnableStatus.Idle(
      accountID = accountID,
      status = if (syncable.account.preferences.bookmarkSyncingPermitted) {
        SYNC_ENABLED
      } else {
        SYNC_DISABLED
      }
    )
  }

  override fun bookmarkSyncEnable(
    accountID: AccountID,
    enabled: Boolean
  ): FluentFuture<ReaderBookmarkSyncEnableResult> {
    return try {
      this.bookmarkEventsOut.onNext(
        ReaderBookmarkEvent.ReaderBookmarkSyncSettingChanged(
          accountID = accountID,
          status = ReaderBookmarkSyncEnableStatus.Changing(accountID)
        )
      )

      val profile =
        this.profilesController.profileCurrent()
      val syncable =
        RBSyncableAccount.ofAccount(profile.account(accountID))

      if (syncable == null) {
        this.logger.error("bookmarkSyncEnable: account does not support syncing")
        val status = SYNC_ENABLE_NOT_SUPPORTED

        this.bookmarkEventsOut.onNext(
          ReaderBookmarkEvent.ReaderBookmarkSyncSettingChanged(
            accountID = accountID,
            status = ReaderBookmarkSyncEnableStatus.Idle(accountID, status)
          )
        )

        return FluentFuture.from(Futures.immediateFuture(status))
      }

      this.accountsSyncChanging.add(accountID)

      val opEnableSync =
        RBServiceOpEnableSync(
          logger = this.logger,
          accountsSyncChanging = this.accountsSyncChanging,
          bookmarkEventsOut = this.bookmarkEventsOut,
          httpCalls = this.httpCalls,
          profile = profile,
          syncableAccount = syncable,
          enable = enabled
        )

      val opCheck =
        RBServiceOpCheckSyncStatusForProfile(
          logger = this.logger,
          httpCalls = this.httpCalls,
          profile = profile
        )

      val enableFuture =
        FluentFuture.from(this.executor.submit(opEnableSync))
          .transform(
            { result ->
              opCheck.call()
              result
            },
            this.executor
          )

      enableFuture.addListener({ this.onAccountEnabledSyncing() }, this.executor)
      enableFuture
    } catch (e: ProfileNoneCurrentException) {
      this.logger.error("bookmarkSyncEnable: no profile is current: ", e)
      FluentFuture.from(Futures.immediateFailedFuture(e))
    }
  }

  override fun bookmarkLoad(
    accountID: AccountID,
    book: BookID
  ): FluentFuture<ReaderBookmarks> {
    return try {
      FluentFuture.from(
        this.executor.submit(
          RBServiceOpLoadBookmarks(
            logger = this.logger,
            accountID = accountID,
            profile = profilesController.profileCurrent(),
            book = book
          )
        )
      )
    } catch (e: ProfileNoneCurrentException) {
      this.logger.error("bookmarkLoad: no profile is current: ", e)
      FluentFuture.from(Futures.immediateFailedFuture(e))
    }
  }

  override fun bookmarkCreate(
    accountID: AccountID,
    bookmark: Bookmark
  ): FluentFuture<Unit> {
    return try {
      FluentFuture.from(
        this.executor.submit(
          RBServiceOpCreateBookmark(
            logger = this.logger,
            objectMapper = this.objectMapper,
            bookmarkEventsOut = this.bookmarkEventsOut,
            httpCalls = this.httpCalls,
            profile = profilesController.profileCurrent(),
            accountID = accountID,
            bookmark = bookmark
          )
        )
      )
    } catch (e: ProfileNoneCurrentException) {
      this.logger.error("bookmarkLoad: no profile is current: ", e)
      FluentFuture.from(Futures.immediateFailedFuture(e))
    }
  }

  override fun bookmarkDelete(
    accountID: AccountID,
    bookmark: Bookmark
  ): FluentFuture<Unit> {
    return try {
      FluentFuture.from(
        this.executor.submit(
          RBServiceOpDeleteBookmark(
            logger = this.logger,
            httpCalls = this.httpCalls,
            profile = profilesController.profileCurrent(),
            accountID = accountID,
            bookmark = bookmark
          )
        )
      )
    } catch (e: ProfileNoneCurrentException) {
      this.logger.error("bookmarkLoad: no profile is current: ", e)
      FluentFuture.from(Futures.immediateFailedFuture(e))
    }
  }
}
