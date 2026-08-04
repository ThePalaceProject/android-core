package org.thepalaceproject.palace.battery

import android.app.Application
import android.content.Context
import android.os.PowerManager
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.Attributes
import org.nypl.simplified.threads.UIThread
import org.slf4j.LoggerFactory

object BatteryModel {

  private var application: Application? = null

  private val logger =
    LoggerFactory.getLogger(BatteryModel::class.java)

  private val attributes =
    Attributes.create { ex -> this.logger.debug("Attribute error: ", ex) }

  private val batteryOptimizerEnabledSrc =
    attributes.withValue(true)

  val batteryOptimizerStatus: AttributeReadableType<Boolean> =
    this.batteryOptimizerEnabledSrc

  fun initialize(
    application: Application
  ) {
    this.application = application
  }

  fun batteryOptimizerCheck() {
    val context = this.application
    if (context != null) {
      UIThread.runOnUIThread {
        val powerManager =
          context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName =
          context.packageName
        this.batteryOptimizerEnabledSrc.set(
          !powerManager.isIgnoringBatteryOptimizations(packageName)
        )
      }
    }
  }
}
