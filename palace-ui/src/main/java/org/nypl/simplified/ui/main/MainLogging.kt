package org.nypl.simplified.ui.main

import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.android.LogcatAppender
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import ch.qos.logback.core.util.FileSize
import ch.qos.logback.core.util.StatusPrinter
import org.librarysimplified.ui.BuildConfig
import org.nypl.simplified.crashlytics.api.CrashlyticsLoggingAppender
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Functions to configure logging.
 */

object MainLogging {

  /**
   * Configure the default logcat appender.
   */

  private fun configureLogcatAppender(
    loggerContext: LoggerContext
  ): Appender<ILoggingEvent> {
    val encoder =
      PatternLayoutEncoder().apply {
        this.context = loggerContext
        this.pattern = "%msg%n"
        this.start()
      }
    return LogcatAppender().apply {
      this.context = loggerContext
      this.name = "LOGCAT"
      this.encoder = encoder
      this.start()
    }
  }

  /**
   * Configure the Crashlytics appender.
   */

  private fun configureCrashlyticsAppender(
    loggerContext: LoggerContext
  ): Appender<ILoggingEvent> {
    return CrashlyticsLoggingAppender().apply {
      this.context = loggerContext
      this.name = "CRASHLYTICS"
      this.start()
    }
  }

  /**
   * Configure a file based logging appender.
   */

  fun configureFileAppender(
    loggerContext: LoggerContext,
    cacheDirectory: File,
    filename: String
  ): Appender<ILoggingEvent> {
    val encoder =
      PatternLayoutEncoder().apply {
        this.context = loggerContext
        this.pattern = "%d{\"yyyy-MM-dd'T'HH:mm:ss,SSS\"} %level %logger{128} - %msg%n"
        this.start()
      }
    val rollingPolicy =
      TimeBasedRollingPolicy<ILoggingEvent>().apply {
        this.context = loggerContext
        this.fileNamePattern = "$filename.%d.gz"
        this.maxHistory = 7
        this.setTotalSizeCap(FileSize.valueOf("10MB"))
      }
    val fileAppender =
      RollingFileAppender<ILoggingEvent>().apply {
        this.context = loggerContext
        this.encoder = encoder
        this.file = File(File(cacheDirectory, "logs"), filename).absolutePath
        this.name = "FILE"
        this.rollingPolicy = rollingPolicy
      }

    // The `TimeBasedRollingPolicy` needs to have a parent set or it will throw a
    // `NullPointerException` when started.
    rollingPolicy.setParent(fileAppender)
    rollingPolicy.start()

    // The `RollingFileAppender` will refuse to start unless the `TriggeringPolicy`,
    // in this case `TimeBasedRollingPolicy`, has started first.
    fileAppender.start()

    return AsyncAppender().apply {
      this.context = loggerContext
      this.name = "ASYNC_FILE"
      this.addAppender(fileAppender)
      this.start()
    }
  }

  private fun logbackOf(
    name: String,
    process: (Logger) -> Unit
  ) {
    (LoggerFactory.getLogger(name) as Logger).apply {
      process(this)
    }
  }

  private fun logbackLevel(
    name: String,
    level: Level
  ) {
    logbackOf(name) {
      it.level = level
    }
  }

  private fun configureLoggingPolicy() {
    logbackLevel("com.io7m.blackthorne.core.internal.BTStackHandler", Level.ERROR)
    logbackLevel("com.io7m.dixmont.core.DmJsonRestrictedDeserializers", Level.ERROR)
    logbackLevel("one.irradia.fieldrush.vanilla.FRParsers", Level.INFO)
    logbackLevel("org.librarysimplified.http.vanilla.internal.LSHTTPTimingEventListener", Level.INFO)
    logbackLevel("org.nypl.simplified.books.covers.BookCoverGenerator", Level.ERROR)
    logbackLevel("org.nypl.simplified.books.covers.BookCoverProvider", Level.ERROR)
    logbackLevel("org.nypl.simplified.files.FileLocking", Level.ERROR)
    logbackLevel("org.nypl.simplified.notifications.NotificationsService", Level.DEBUG)
    logbackLevel("org.sqlite.core.NativeDB", Level.ERROR)
  }

  fun configure(cacheDirectory: File) {
    val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext

    // Reset the default logging context so we can reconfigure it
    loggerContext.stop()

    // Send logger status messages to System.out
    loggerContext.statusManager.apply {
      this.add { StatusPrinter.print(listOf(it)) }
    }

    val root =
      LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger

    // Set our log level
    root.level = if (BuildConfig.DEBUG) {
      Level.TRACE
    } else {
      Level.DEBUG
    }

    // Add appenders to the root logger
    root.apply {
      this.addAppender(configureFileAppender(loggerContext, cacheDirectory, "log.txt"))
      this.addAppender(configureLogcatAppender(loggerContext))
      this.addAppender(configureCrashlyticsAppender(loggerContext))
    }

    configureLoggingPolicy()
  }
}
