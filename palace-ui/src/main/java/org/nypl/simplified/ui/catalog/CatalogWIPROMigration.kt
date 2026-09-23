package org.nypl.simplified.ui.catalog

import com.io7m.verona.core.Version
import org.librarysimplified.services.api.Services
import org.nypl.simplified.adobe.extensions.AdobeDRMExtensions
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory

object CatalogWIPROMigration {
  private val logger =
    LoggerFactory.getLogger(CatalogWIPROMigration::class.java)

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
    bookFormat: BookFormat
  ): Boolean {
    val drmInfo = bookFormat.drmInformation
    if (drmInfo !is BookDRMInformation.ACS) {
      this.logger.debug("WIPRO: Book is not using ACS DRM, no migration required.")
      return false
    }

    /*
     * The Adobe binaries are WIPRO binaries if the major version of the package is >= 5. If
     * the current version is less than 5, then we have no migration to perform (yet!).
     */

    val versionCurrent = AdobeDRMExtensions.versionCurrent()
    if (versionCurrent.major < 5) {
      this.logger.debug("WIPRO: Current version is {}, no migration required.", versionCurrent)
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
     * If no version was recorded with the activation, that means it was performed using software
     * old enough to require a migration.
     */

    val versionActivated: Version? = adobePost.version
    if (versionActivated == null) {
      this.logger.debug("WIPRO: No recorded version; migration is required!")
      return true
    }

    /*
     * If the recorded version is older than the first WIPRO binaries, then it means we need to
     * perform a migration.
     */

    if (versionActivated.major() < 5) {
      this.logger.debug("WIPRO: Recorded version is {}; migration is required!", versionActivated)
      return true
    }

    this.logger.debug("WIPRO: No migration required.")
    return false
  }

  fun executeNow(
    book: Book,
    bookFormat: BookFormat
  ) {
  }
}
