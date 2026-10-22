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
   * local write failure, parse failure, ...) is carried in the `Reason` data field.
   */

  const val TIME_TRACKING_LOSS = "TimeTrackingLoss"

  /**
   * An EPUB book failed to open (e.g. a DRM decryption failure, a content-protection failure,
   * or a corrupted/unreadable content file). The failing stage (account lookup, format lookup,
   * or reader open) is carried in the `Reason` data field; the specific cause is carried by the
   * associated throwable and the `ExceptionType` data field.
   */

  const val EPUB_BOOK_OPEN_FAILED = "EPUBBookOpenFailed"

  /**
   * A PDF book failed to open (e.g. a content-protection failure or a failure to start the
   * local PDF server). The failing stage (account lookup, format lookup, or PDF server
   * creation) is carried in the `Reason` data field; the specific cause is carried by the
   * associated throwable and the `ExceptionType` data field.
   */

  const val PDF_BOOK_OPEN_FAILED = "PDFBookOpenFailed"

  /**
   * A book preview failed to open in the reader. The failing stage (account lookup, format
   * inference, or reader open) is carried in the `Reason` data field; the specific cause is
   * carried by the associated throwable and the `ExceptionType` data field.
   */

  const val PREVIEW_BOOK_OPEN_FAILED = "PreviewBookOpenFailed"

  /**
   * An audiobook failed to open. The failing stage (opening the player, downloading the
   * manifest, license checks, or manifest parsing) is carried in the `Reason` data field; the
   * book-open failure additionally carries the associated throwable and the `ExceptionType`
   * data field.
   */

  const val AUDIOBOOK_BOOK_OPEN_FAILED = "AudiobookBookOpenFailed"

  /**
   * No viewer provider could handle a book; the book's format is not supported by any viewer
   * and the book cannot be opened. The unsupported format is carried in the `BookFormat` data
   * field.
   */

  const val NO_VIEWER_FOR_BOOK = "NoViewerForBook"

  /**
   * Restoring the audiobook last-read position timed out while waiting for bookmarks. Playback
   * proceeds without the saved position. The originating ticket is carried in the `Ticket` data
   * field.
   */

  const val AUDIOBOOK_BOOKMARK_TIMEOUT = "AudiobookBookmarkTimeout"

  /**
   * A test diagnostic event, reported when the user presses the "Send Diagnostic" button in the
   * debug menu. It carries no real data; it exists solely to verify end-to-end that a reported
   * event (code plus custom keys) reaches the diagnostics backend.
   */

  const val USER_PRESSED_DEBUG_DIAGNOSTIC_BUTTON = "UserPressedDebugDiagnosticButton"
}
