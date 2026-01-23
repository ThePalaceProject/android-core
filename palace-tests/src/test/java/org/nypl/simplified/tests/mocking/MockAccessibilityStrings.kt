package org.nypl.simplified.tests.mocking

import org.nypl.simplified.accessibility.AccessibilityStringsType

class MockAccessibilityStrings : AccessibilityStringsType {
  override fun bookHasDownloaded(title: String): String = "bookHasDownloaded $title"
  override fun bookIsDownloading(title: String): String = "bookIsDownloading $title"
  override fun bookIsOnHold(title: String): String = "bookIsOnHold $title"
  override fun bookReturned(title: String): String = "bookReturned $title"
  override fun bookFailedReturn(title: String): String = "bookFailedReturn $title"
  override fun bookFailedLoan(title: String): String = "bookFailedLoan $title"
  override fun bookFailedDownload(title: String): String = "bookFailedDownload $title"
  override fun bookLoanLimitReached(title: String): String = "bookLoanLimitReached $title"
  override fun bookFailedLoanLoginRequired(title: String): String = "bookFailedLoanLoginRequired $title"
  override fun bookFailedRevokeLoginRequired(title: String): String = "bookFailedRevokeLoginRequired $title"
  override fun bookFailedDownloadLoginRequired(title: String): String = "bookFailedDownloadLoginRequired $title"
}
