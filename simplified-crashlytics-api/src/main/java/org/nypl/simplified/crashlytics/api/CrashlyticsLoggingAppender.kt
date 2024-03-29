package org.nypl.simplified.crashlytics.api

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.status.WarnStatus
import java.util.ServiceLoader

/**
 * A Logback appender that writes exceptions to a Crashlytics service backend.
 *
 * When instantiated without an explicit [CrashlyticsServiceType] the appender will
 * attempt to load a valid service from the system's [ServiceLoader].
 *
 * The appender will not be started if no valid service can be found.
 */

class CrashlyticsLoggingAppender(
  var service: CrashlyticsServiceType? = null
) : AppenderBase<ILoggingEvent>() {

  init {
    if (this.service == null) {
      val loader =
        ServiceLoader.load(CrashlyticsServiceType::class.java)

      loader.count().let { count ->
        if (count > 1) {
          addStatus(WarnStatus("More than one [$count] usable service!", this))
        }
      }
      this.service = loader.firstOrNull()
    }
  }

  override fun start() {
    if (this.service == null) {
      return addStatus(
        WarnStatus("Found no usable ${CrashlyticsServiceType::class.simpleName}!", this)
      )
    }
    super.start()
  }

  override fun append(event: ILoggingEvent) {
    val proxy = event.throwableProxy as? ThrowableProxy
    val throwable = proxy?.throwable

    val srv = this.service ?: return
    val mdcData = event.mdcPropertyMap
    for (entry in mdcData) {
      srv.setCustomKey(entry.key, entry.value)
    }
    srv.log(event.formattedMessage)
    if (throwable != null) {
      srv.recordException(throwable)
    }
  }
}
