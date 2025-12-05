package org.nypl.simplified.ui.main

import android.app.Application
import android.os.Process
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeSubscriptionType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.services.api.ServiceDirectoryType
import org.librarysimplified.ui.BuildConfig
import org.nypl.simplified.boot.api.BootEvent
import org.nypl.simplified.boot.api.BootLoader
import org.nypl.simplified.boot.api.BootProcessType
import org.nypl.simplified.threads.UIThread
import org.slf4j.LoggerFactory

class MainApplication : Application() {

  companion object {
    private lateinit var INSTANCE: MainApplication

    @JvmStatic
    val application: MainApplication
      get() = this.INSTANCE
  }

  private val logger =
    LoggerFactory.getLogger(MainApplication::class.java)

  private val boot: BootLoader<ServiceDirectoryType> =
    BootLoader(
      bootProcess = object : BootProcessType<ServiceDirectoryType> {
        override fun execute(onProgress: (BootEvent) -> Unit): ServiceDirectoryType {
          return MainServices.setup(this@MainApplication, onProgress)
        }
      },
      bootStringResources = ::MainServicesStrings
    )

  private val attributes =
    Attributes.create { ex ->
      this.logger.error("Uncaught exception in attribute handling: ", ex)
    }

  /**
   * Read events from the given readable attribute, and republish them to the target attribute
   * such that all updates will be observed on the Android UI thread.
   */

  private fun <T> wrapAttribute(
    source: AttributeReadableType<T>,
    target: AttributeType<T>
  ): AttributeSubscriptionType {
    return source.subscribe { _, newValue -> UIThread.runOnUIThread { target.set(newValue) } }
  }

  private val bootEventsUI: AttributeType<BootEvent> =
    this.attributes.withValue(BootEvent.BootInProgress("Booting..."))

  init {
    this.wrapAttribute(this.boot.events, this.bootEventsUI)
  }

  override fun onCreate() {
    super.onCreate()

    MainLogging.configure(this.cacheDir)
    this.configureStrictMode()
    this.logStartup()
    MainTransifex.configure(this.applicationContext)
    PlayerModel.start(this)

    org.nypl.simplified.ui.main.MainApplication.Companion.INSTANCE = this
    this.boot.start(this)
  }

  private fun logStartup() {
    this.logger.debug("starting app: pid {}", Process.myPid())
    this.logger.debug("app version: {}", BuildConfig.SIMPLIFIED_VERSION)
    this.logger.debug("app build:   {}", this.versionCode())
    this.logger.debug("app commit:  {}", BuildConfig.SIMPLIFIED_GIT_COMMIT)
  }

  private fun versionCode(): String {
    return try {
      val info = this.packageManager.getPackageInfo(this.packageName, 0)
      info.versionCode.toString()
    } catch (e: Exception) {
      this.logger.debug("version info unavailable: ", e)
      "UNKNOWN"
    }
  }

  /**
   * StrictMode is a developer tool which detects things you might be doing by accident and
   * brings them to your attention so you can fix them.
   *
   * StrictMode is most commonly used to catch accidental disk or network access on the
   * application's main thread, where UI operations are received and animations take place.
   */

  private fun configureStrictMode() {
    if (BuildConfig.DEBUG) {
      StrictMode.setThreadPolicy(
        ThreadPolicy.Builder()
          .detectDiskReads()
          .detectDiskWrites()
          .detectNetwork()
          .penaltyLog()
          .build()
      )
      StrictMode.setVmPolicy(
        VmPolicy.Builder()
          .detectLeakedSqlLiteObjects()
          .detectLeakedClosableObjects()
          .penaltyLog()
          .build()
      )
    }
  }

  /**
   * An observable value that publishes events as the application is booting. Events
   * are guaranteed to be observed on the Android UI thread.
   */

  val servicesBootEvents: AttributeReadableType<BootEvent>
    get() = this.bootEventsUI
}
