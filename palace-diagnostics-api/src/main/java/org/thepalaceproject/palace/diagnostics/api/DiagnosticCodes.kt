package org.thepalaceproject.palace.diagnostics.api

/**
 * Stable, low-cardinality identifiers for notable anomalies reported via
 * [DiagnosticsServiceType].
 *
 * Codes must not contain unique values (user IDs, book IDs, timestamps); such values belong
 * in the per-report data map. A high cardinality of codes can cause the diagnostics backend
 * to throttle reporting.
 */

object DiagnosticCodes {
  /**
   * A book action (borrow, SAML download, ...) was requested for an account that no longer
   * exists in the profile. The action cannot be recovered and is a no-op.
   */

  const val CATALOG_BOOK_ACTION_STALE_ACCOUNT = "CatalogBookActionStaleAccount"

  /**
   * A period of reading/listening time was dropped and will not be reported to the time
   * tracking backend. The reason for the loss (book switched mid-session, no active book,
   * local write failure, parse failure, ...) is carried in the `reason` data field.
   */

  const val TIME_TRACKING_LOSS = "TimeTrackingLoss"

  /**
   * A book failed to open (e.g. a DRM decryption failure or a corrupted/unreadable content
   * file). The specific cause is carried by the associated throwable and the
   * `exceptionType` data field.
   */

  const val BOOK_OPEN_FAILED = "BookOpenFailed"

  /**
   * Restoring the audiobook last-read position timed out while waiting for bookmarks. Playback
   * proceeds without the saved position. The originating ticket is carried in the `ticket`
   * data field.
   */

  const val AUDIOBOOK_BOOKMARK_TIMEOUT = "AudiobookBookmarkTimeout"
}
