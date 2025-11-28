package org.nypl.simplified.boot.api

import android.content.Context
import android.content.res.Resources
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import org.nypl.simplified.presentableerror.api.PresentableErrorType
import org.slf4j.LoggerFactory
import java.util.ServiceLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * A class for starting up a set of services on a background thread and publishing
 * events as the services are started.
 */

class BootLoader<T>(

  /**
   * The string resources used by the boot process.
   */

  private val bootStringResources: (Resources) -> BootStringResourcesType,

  /**
   * A function that sets up services.
   */

  private val bootProcess: BootProcessType<T>
) : BootLoaderType<T> {

  private val logger =
    LoggerFactory.getLogger(BootLoader::class.java)

  private val attributes =
    Attributes.create { ex ->
      this.logger.error("Uncaught exception in attribute handling: ", ex)
    }

  private val executor =
    Executors.newFixedThreadPool(1) { runnable ->
      val thread = Thread(runnable)
      thread.priority = Thread.MIN_PRIORITY
      thread.name = "simplified-boot-${thread.id}"
      thread
    }

  private val eventsActual: AttributeType<BootEvent> =
    this.attributes.withValue(BootEvent.BootInProgress("Booting..."))

  private val bootLock: Any = Any()
  private var boot: CompletableFuture<T>? = null

  override val events: AttributeReadableType<BootEvent> =
    this.eventsActual

  override fun start(context: Context): CompletableFuture<T> {
    return synchronized(this.bootLock) {
      if (this.boot == null) {
        this.boot = this.runBoot(context)
      }
      this.boot!!
    }
  }

  private class PresentableException(
    override val message: String,
    override val cause: Throwable
  ) : Exception(message, cause), PresentableErrorType

  private fun runBoot(context: Context): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    this.executor.execute {
      val strings = this.bootStringResources.invoke(context.resources)

      this.executeBootPreHooks(context)

      try {
        future.complete(this.bootProcess.execute { event -> this.eventsActual.set(event) })
        this.logger.debug("finished executing boot")
      } catch (e: Throwable) {
        this.logger.error("boot failed: ", e)
        val event = if (e is PresentableErrorType) {
          BootEvent.BootFailed(
            message = e.message,
            exception = PresentableException(e.message, e),
            attributes = e.attributes
          )
        } else {
          BootEvent.BootFailed(
            message = strings.bootFailedGeneric,
            exception = PresentableException(strings.bootFailedGeneric, e)
          )
        }

        this.eventsActual.set(event)
        future.completeExceptionally(event.exception)
      }
    }
    return future
  }

  private fun executeBootPreHooks(context: Context) {
    try {
      val hooks = ServiceLoader.load(BootPreHookType::class.java).toList()
      this.logger.debug("executing {} boot pre-hooks", hooks.size)

      for (hook in hooks) {
        try {
          hook.execute(context)
        } catch (e: Throwable) {
          this.logger.error("failed to execute boot pre-hook {}: ", hook, e)
        }
      }
    } catch (e: Throwable) {
      this.logger.error("failed to execute boot pre-hook: ", e)
    }
  }
}
