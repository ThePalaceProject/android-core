package org.thepalaceproject.palace.diagnostics.api

/**
 * A no-op diagnostics service. Used when no backend implementation is available so that
 * [DiagnosticsServiceType] is always present in the service directory.
 */

internal object NoOpDiagnosticsService : DiagnosticsServiceType {
  override fun report(
    code: String,
    data: Map<String, String>,
    throwable: Throwable?
  ) {
    // No-op.
  }
}
