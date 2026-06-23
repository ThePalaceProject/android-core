package org.nypl.simplified.ui.settings

import android.os.PowerManager
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.Attributes
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.main.MainApplication
import org.slf4j.LoggerFactory
import android.content.Context

object SettingsModel {

  private val logger =
    LoggerFactory.getLogger(SettingsModel::class.java)

  private var debugClicks = 0

  private val attributes =
    Attributes.create { ex -> this.logger.debug("Attribute error: ", ex) }

  private val batteryOptimizerEnabledSrc =
    attributes.withValue(true)

  val batteryOptimizerStatus: AttributeReadableType<Boolean> =
    this.batteryOptimizerEnabledSrc

  fun batteryOptimizerCheck() {
    UIThread.runOnUIThread {
      val powerManager =
        MainApplication.application.getSystemService(Context.POWER_SERVICE) as PowerManager
      val packageName =
        MainApplication.application.packageName
      this.batteryOptimizerEnabledSrc.set(
        !powerManager.isIgnoringBatteryOptimizations(packageName)
      )
    }
  }

  fun onClickVersion(
    profiles: ProfilesControllerType
  ) {
    ++this.debugClicks
    if (this.debugClicks >= 7) {
      this.debugClicks = 0
      profiles.profileUpdate { d ->
        d.copy(
          preferences = d.preferences.copy(
            showDebugSettings = !d.preferences.showDebugSettings
          )
        )
      }
    }
  }

  fun showDebugSettings(
    profiles: ProfilesControllerType
  ): Boolean {
    return profiles
      .profileCurrent()
      .preferences()
      .showDebugSettings
  }
}
