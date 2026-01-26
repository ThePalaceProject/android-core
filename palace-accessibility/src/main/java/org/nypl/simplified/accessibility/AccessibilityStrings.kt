package org.nypl.simplified.accessibility

import android.content.res.Resources
import org.librarysimplified.accessibility.R

/**
 * The default implementation of the [AccessibilityStringsType].
 */

class AccessibilityStrings(
  private val resources: Resources
) : AccessibilityStringsType {
  override fun bookHasDownloaded(title: String): String =
    this.resources.getString(R.string.accessBookHasDownloaded, title)

  override fun bookIsDownloading(title: String): String =
    this.resources.getString(R.string.accessBookIsDownloading, title)

  override fun bookIsOnHold(title: String): String =
    this.resources.getString(R.string.accessBookIsOnHold, title)

  override fun bookReturned(title: String): String =
    this.resources.getString(R.string.accessBookReturned, title)

  override fun bookFailedReturn(title: String): String =
    this.resources.getString(R.string.accessBookFailedReturn, title)

  override fun bookFailedLoan(title: String): String =
    this.resources.getString(R.string.accessBookFailedLoan, title)

  override fun bookFailedDownload(title: String): String =
    this.resources.getString(R.string.accessBookFailedDownload, title)

  override fun bookLoanLimitReached(title: String): String =
    this.resources.getString(R.string.accessBookReachedLoanLimit, title)

  override fun bookFailedLoanLoginRequired(title: String): String =
    this.resources.getString(R.string.accessBookFailedLoanLoginRequired, title)

  override fun bookFailedRevokeLoginRequired(title: String): String =
    this.resources.getString(R.string.accessBookFailedRevokeLoginRequired, title)

  override fun bookFailedDownloadLoginRequired(title: String): String =
    this.resources.getString(R.string.accessBookFailedDownloadLoginRequired, title)
}
