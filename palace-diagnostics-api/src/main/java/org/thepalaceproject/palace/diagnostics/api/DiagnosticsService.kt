package org.thepalaceproject.palace.diagnostics.api

import java.util.ServiceLoader

/**
 * A factory for [DiagnosticsServiceType] instances.
 */

object DiagnosticsService {
  /**
   * Create a [DiagnosticsServiceType], loading the first available implementation from the
   * [ServiceLoader]. If no implementation is present, a no-op service is returned so that
   * the type is always available.
   */

  fun create(): DiagnosticsServiceType =
    ServiceLoader
      .load(DiagnosticsServiceType::class.java)
      .firstOrNull()
      ?: NoOpDiagnosticsService
}
