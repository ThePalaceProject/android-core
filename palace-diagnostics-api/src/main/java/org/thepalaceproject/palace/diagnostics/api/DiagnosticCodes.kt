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
   * An Adobe DRM device activation failed. The DRM error code (if one was raised by the
   * connector) is carried in the `DRMErrorCode` data field; otherwise the exception type is
   * carried in the `ExceptionType` data field.
   */

  const val ADOBE_DRM_ACTIVATION_FAILED = "AdobeDRMActivationFailed"

  /**
   * An Adobe DRM device deactivation failed. The DRM error code (if one was raised by the
   * connector) is carried in the `DRMErrorCode` data field; otherwise the exception type is
   * carried in the `ExceptionType` data field.
   */

  const val ADOBE_DRM_DEACTIVATION_FAILED = "AdobeDRMDeactivationFailed"

  /**
   * An Adobe DRM fulfillment (ACSM download) failed. The DRM error code (if one was raised by
   * the connector) is carried in the `DRMErrorCode` data field; otherwise the exception type
   * is carried in the `ExceptionType` data field.
   */

  const val ADOBE_DRM_FULFILLMENT_FAILED = "AdobeDRMFulfillmentFailed"

  /**
   * A period of reading/listening time was dropped and will not be reported to the time
   * tracking backend. The reason for the loss (book switched mid-session, no active book,
   * local write failure, parse failure, ...) is carried in the `Reason` data field.
   */

  const val TIME_TRACKING_LOSS = "TimeTrackingLoss"

  /**
   * The migration of the legacy file-based time tracking data into the database encountered
   * a problem. The specific problem is carried in the `Reason` data field, and the affected
   * file (if any) is carried in the `File` data field. No time tracking data was deleted
   * without being successfully migrated, with the exception of the legacy event log, where a
   * partial tail record may be skipped.
   */

  const val TIME_TRACKING_MIGRATION = "TimeTrackingMigration"

  /**
   * An attempt to record an event in the time tracking event log failed. The event log is an
   * audit aid; its failure does not affect the integrity of the time tracking data itself.
   * The failing operation is carried in the `Operation` data field.
   */

  const val TIME_TRACKING_AUDIT = "TimeTrackingAudit"

  /**
   * The time tracking pipeline held entries for an account that no longer exists in the
   * profile, and so could not send them. The entries are retained in the database so that
   * they can be inspected and settled manually; they will not be retried. The account
   * identifier is carried in the `Account` data field.
   */

  const val TIME_TRACKING_ORPHANED = "TimeTrackingOrphaned"

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

  /**
   * The application boot process failed. The boot process is not expected to fail; when it
   * does, the application is unusable and the failure is reported so that it can be observed
   * in Crashlytics. The type of the failing exception is carried in the `ExceptionType` data
   * field; the specific cause is carried by the associated throwable.
   */

  const val BOOT_FAILED = "BootFailed"

  /**
   * A value in the managed device (MDM) configuration was present but could not be parsed.
   * The failing field and its value are carried in the `Field` and `Value` data fields.
   */

  const val MDM_CONFIGURATION_INVALID_FIELD = "MDMConfigurationInvalidField"

  /**
   * An account for a library named in the managed device (MDM) configuration could not be
   * created at start-up. The provider URI is carried in the `Provider` data field; the
   * exception type is carried in the `ExceptionType` data field.
   */

  const val MDM_LIBRARY_CREATE_FAILED = "MDMLibraryCreateFailed"
}
