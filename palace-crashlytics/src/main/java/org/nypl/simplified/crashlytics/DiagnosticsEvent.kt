package org.nypl.simplified.crashlytics

/**
 * A synthetic throwable used to record a notable anomaly in Crashlytics when the anomaly has
 * no real [Throwable] of its own. The [code] is used as the message so that Crashlytics
 * groups these synthetic reports by code.
 */

class DiagnosticsEvent(
  val code: String
) : RuntimeException(code)
