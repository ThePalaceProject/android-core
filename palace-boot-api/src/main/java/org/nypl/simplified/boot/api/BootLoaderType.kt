package org.nypl.simplified.boot.api

import android.content.Context
import com.io7m.jattribute.core.AttributeReadableType
import java.util.concurrent.CompletableFuture

/**
 * The type of boot loaders.
 *
 * A boot loader is a class that starts up application services on a background thread and
 * publishes events as the services start.
 */

interface BootLoaderType<T> {

  /**
   * An observable that publishes events during the boot process.
   */

  val events: AttributeReadableType<BootEvent>

  /**
   * Start the boot process if it has not already started, and return a future representing
   * the boot in progress.
   */

  fun start(context: Context): CompletableFuture<T>
}
