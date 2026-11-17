package org.thepalaceproject.mdm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.slf4j.LoggerFactory

/**
 * A watcher for the managed device (MDM) configuration.
 *
 * When the watcher is started, it applies the current configuration.
 * It then re-applies the configuration whenever the device management
 * system changes it; a configuration that has not changed since it was
 * last applied is not re-applied.
 *
 * The watcher is started once, when the application starts. As a
 * result, a configuration change that occurs while the application is
 * not running is picked up when the application next starts.
 *
 * How a configuration is applied — which profile it affects, what it
 * changes in that profile, and how failures are handled — is the
 * responsibility of [MDMLibraryApplier], to which the watcher
 * delegates.
 */
class MDMConfigurationWatcher(
  private val context: Context,
  private val reader: MDMConfigurationReader,
  private val applier: MDMLibraryApplier,
) {
  private val logger =
    LoggerFactory.getLogger(MDMConfigurationWatcher::class.java)

  private var lastApplied: MDMConfigurationParseResult? = null
  private var receiver: BroadcastReceiver? = null

  /**
   * Apply the current configuration, and begin watching for changes to
   * it.
   */
  fun start() {
    this.applyCurrent()
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(
          context: Context?,
          intent: Intent?,
        ) {
          this@MDMConfigurationWatcher.applyCurrent()
        }
      }
    this.receiver = receiver
    this.context.registerReceiver(
      receiver,
      IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED),
      Context.RECEIVER_NOT_EXPORTED,
    )
  }

  /**
   * Stop watching for configuration changes.
   */
  fun stop() {
    val receiver =
      this.receiver
    if (receiver != null) {
      this.context.unregisterReceiver(receiver)
      this.receiver = null
    }
  }

  private fun applyCurrent() {
    val result =
      this.reader.readConfiguration()
    if (result == this.lastApplied) {
      this.logger.debug("MDM configuration unchanged ({}); not re-applying", result)
      return
    }
    this.logger.debug("MDM configuration changed from {} to {}; applying", this.lastApplied, result)
    this.lastApplied = result
    this.applier.apply(result)
  }
}
