package org.thepalaceproject.palace.diagnostics.api

/**
 * A service for reporting notable anomalies to a diagnostics backend.
 *
 * This is a low-volume, high-signal channel for specific, mostly-silent failures that do not
 * otherwise produce a crash or a user-visible error, but for which fleet-wide visibility is
 * wanted without having to request on-device logs. It is not a replacement for normal
 * logging.
 */

interface DiagnosticsServiceType {
  /**
   * Report a notable anomaly.
   *
   * @param code A stable, low-cardinality, self-documenting identifier for the kind of
   *             anomaly (e.g. `CatalogBookActionStaleAccount`). Must not contain unique
   *             values (user IDs, book IDs, timestamps); those belong in [data].
   * @param data A flat map of minimal, structured, identifying context. All values are
   *             strings.
   * @param throwable A real exception, if there is one. When `null`, the implementation
   *             synthesises one.
   */
  fun report(
    code: String,
    data: Map<String, String>,
    throwable: Throwable? = null
  )
}
