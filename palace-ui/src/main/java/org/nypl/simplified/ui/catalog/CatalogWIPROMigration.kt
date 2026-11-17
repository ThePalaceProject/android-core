package org.nypl.simplified.ui.catalog

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.MoreExecutors
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.Attributes
import org.librarysimplified.http.api.LSHTTPNetworkAccessType
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountOIDC
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.adobe.extensions.AdobeDRMExtensions
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.futures.FluentFutureExtensions.flatMap
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.UIThread
import org.slf4j.LoggerFactory

object CatalogWIPROMigration {
  private val logger =
    LoggerFactory.getLogger(CatalogWIPROMigration::class.java)

  private val attributes =
    Attributes.create { e -> this.logger.debug("Attribute error: ", e) }

  private val migrationInProgressRef =
    this.attributes.withValue(false)

  /**
   * An attribute that indicates whether a migration is in process. Values are always published
   * on the UI thread.
   */

  val migrationInProgress: AttributeReadableType<Boolean> =
    this.migrationInProgressRef

  /**
   * Check to see if we need to perform a "migration" for Adobe/WIPRO.
   *
   * Essentially: In early 2026, WIPRO took over from Adobe, and this meant new binaries and a
   * new server. If the current application has a device activation that was produced with pre-WIPRO
   * binaries, then we must log out, log back in (to activate again), and then re-download the
   * current book. We check to see if this migration is necessary by checking the version number
   * that was recorded in the post-activation credentials. We treat the lack of a version number
   * as being "pre-WIPRO".
   */

  fun isMigrationRequired(
    book: Book,
    bookFormat: BookFormat,
  ): Boolean {
    val drmInfo = bookFormat.drmInformation
    if (drmInfo !is BookDRMInformation.ACS) {
      this.logger.debug("WIPRO: Book is not using ACS DRM, no migration required.")
      return false
    }

    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    val accountID =
      book.account
    val account =
      profiles
        .profileCurrent()
        .account(accountID)

    val credentials = account.loginState.credentials
    if (credentials == null) {
      this.logger.debug("WIPRO: Not logged in, no migration required.")
      return false
    }

    val adobePre = credentials.adobeCredentials
    if (adobePre == null) {
      this.logger.debug("WIPRO: Not activated, no migration required.")
      return false
    }

    val adobePost = adobePre.postActivationCredentials
    if (adobePost == null) {
      this.logger.debug("WIPRO: Not activated, no migration required.")
      return false
    }

    /*
     * The decision of whether a recorded activation is stale (performed with a pre-migration
     * binary) is centralised in AdobeDRMExtensions, which also performs the stale-activation
     * deactivation and re-activation during a borrow.
     */

    val required = AdobeDRMExtensions.activationIsStale(adobePost.version)
    if (required) {
      this.logger.debug("WIPRO: Recorded version is {}; migration is required!", adobePost.version)
    } else {
      this.logger.debug("WIPRO: No migration required.")
    }
    return required
  }

  private fun executeMigration(book: Book) {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    val books =
      services.requireService(BooksControllerType::class.java)

    val accountID =
      book.account
    val account =
      profiles
        .profileCurrent()
        .account(accountID)

    val credentials =
      account.loginState.credentials

    val loginRequest: ProfileAccountLoginRequest =
      when (credentials) {
        is AccountAuthenticationCredentials.Basic -> {
          ProfileAccountLoginRequest.Basic(
            accountId = accountID,
            description = account.provider.authentication as AccountProviderAuthenticationDescription.Basic,
            username = credentials.userName,
            password = credentials.password,
          )
        }

        is AccountAuthenticationCredentials.BasicToken -> {
          ProfileAccountLoginRequest.BasicToken(
            accountId = accountID,
            description = account.provider.authentication as AccountProviderAuthenticationDescription.BasicToken,
            username = credentials.userName,
            password = credentials.password,
          )
        }

        is AccountAuthenticationCredentials.OpenIDConnect -> {
          ProfileAccountLoginRequest.OIDCInitiate(
            accountId = accountID,
            description = account.provider.authentication as AccountProviderAuthenticationDescription.OpenIDConnect,
            redirectURI = AccountOIDC.oidcCallbackLoginURI(account.id),
          )
        }

        is AccountAuthenticationCredentials.SAML2_0 -> {
          ProfileAccountLoginRequest.SAML20Initiate(
            accountId = accountID,
            description = account.provider.authentication as AccountProviderAuthenticationDescription.SAML2_0,
          )
        }

        null -> {
          return
        }
      }

    UIThread.runOnUIThread { this.migrationInProgressRef.set(true) }

    val future =
      profiles
        .profileAccountLogout(accountID)
        .flatMap { _ -> profiles.profileAccountLogin(loginRequest) }
        .flatMap { _ -> books.bookBorrow(accountID, book.id, book.entry, null) }

    future.addListener({
      UIThread.runOnUIThread { this.migrationInProgressRef.set(false) }
    }, MoreExecutors.directExecutor())
  }

  fun executeNow(
    context: Context,
    book: Book,
  ) {
    val networkAccess =
      Services
        .serviceDirectory()
        .requireService(LSHTTPNetworkAccessType::class.java)

    if (networkAccess.wifiAvailable.get()) {
      this.executeMigration(book)
      return
    }

    if (networkAccess.cellularAvailable.get()) {
      MaterialAlertDialogBuilder(context)
        .setMessage(R.string.wiproMigrationDownloadAgainCellular)
        .setNegativeButton(R.string.catalogCancel) { dialog, _ -> dialog.dismiss() }
        .setPositiveButton(R.string.catalogDownload) { _, _ -> executeMigration(book) }
        .create()
        .show()
      return
    }

    MaterialAlertDialogBuilder(context)
      .setMessage(R.string.wiproMigrationDownloadAgainNoNetwork)
      .setNegativeButton(R.string.catalogCancel) { dialog, _ -> dialog.dismiss() }
      .create()
      .show()
  }
}
