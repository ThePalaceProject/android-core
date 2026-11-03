package org.nypl.simplified.crashlytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.thepalaceproject.palace.diagnostics.api.DiagnosticsServiceType

/**
 * A [DiagnosticsServiceType] backed by Firebase Crashlytics.
 *
 * Each reported anomaly becomes a Crashlytics *non-fatal* report. When a real [throwable] is
 * supplied it is recorded directly; otherwise a synthetic [DiagnosticsEvent] carrying the
 * [code] is recorded so that the anomaly is still visible as a standalone, groupable report.
 * The [data] entries are recorded as Crashlytics custom keys.
 */

class CrashlyticsDiagnosticsService : DiagnosticsServiceType {
  private val crashlytics =
    FirebaseCrashlytics.getInstance()

  override fun report(
    code: String,
    data: Map<String, String>,
    throwable: Throwable?
  ) {
    for ((key, value) in data) {
      this.crashlytics.setCustomKey(key, value)
    }
    this.crashlytics.recordException(throwable ?: DiagnosticsEvent(code))
  }
}
