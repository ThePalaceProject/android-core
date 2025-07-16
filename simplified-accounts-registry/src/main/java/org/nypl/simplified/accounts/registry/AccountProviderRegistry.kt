package org.nypl.simplified.accounts.registry

import android.content.Context
import com.google.common.base.Preconditions
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import com.io7m.jmulticlose.core.CloseableCollection
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.librarysimplified.http.api.LSHTTPClientType
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountProviderResolutionListenerType
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.api.AccountSearchQuery
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent.SourceFailed
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent.StatusChanged
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent.Updated
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus.Idle
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus.Refreshing
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceFactoryType
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.threads.NamedThreadPools
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.Collections
import java.util.ServiceLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * The default registry of account provider descriptions.
 */

class AccountProviderRegistry private constructor(
  private val context: Context,
  private val sources: List<AccountProviderSourceType>,
  override val defaultProvider: AccountProviderType
) : AccountProviderRegistryType {

  private val logger =
    LoggerFactory.getLogger(AccountProviderRegistry::class.java)

  @Volatile
  private var initialized = false

  private val resolved =
    ConcurrentHashMap<URI, AccountProviderType>()
  private val resolvedReadOnly =
    Collections.unmodifiableMap(this.resolved)

  private val resources =
    CloseableCollection.create()

  private val exec =
    NamedThreadPools.namedThreadPool(1, "registry", 19)

  init {
    this.resources.add(AutoCloseable { this.exec.shutdown() })
  }

  private val attributes =
    Attributes.create { ex -> this.logger.error("Uncaught exception in attribute: ", ex) }

  private val statusAttributeActual: AttributeType<AccountProviderRegistryStatus> =
    this.attributes.withValue(Idle)

  private val accountProviderDescriptionsAttributeActual: AttributeType<Map<URI, AccountProviderDescription>> =
    this.attributes.withValue(mapOf())

  private val eventsActual: PublishSubject<AccountProviderRegistryEvent> =
    PublishSubject.create()

  init {
    this.resources.add(AutoCloseable { this.eventsActual.onComplete() })
  }

  @Deprecated("Use the status attribute instead.")
  override val events: Observable<AccountProviderRegistryEvent> =
    this.eventsActual

  override fun accountProviderDescriptions(): Map<URI, AccountProviderDescription> {
    if (!this.initialized) {
      this.refresh(false)
    }
    return this.accountProviderDescriptionsAttributeActual.get()
  }

  override val resolvedProviders: Map<URI, AccountProviderType>
    get() = this.resolvedReadOnly

  override val statusAttribute: AttributeReadableType<AccountProviderRegistryStatus>
    get() = this.statusAttributeActual

  override val accountProviderDescriptionsAttribute: AttributeReadableType<Map<URI, AccountProviderDescription>>
    get() = this.accountProviderDescriptionsAttributeActual

  override fun refresh(
    includeTestingLibraries: Boolean,
    useCache: Boolean
  ) {
    this.logger.debug("refreshing account provider descriptions")

    this.statusAttributeActual.set(Refreshing)
    this.eventsActual.onNext(StatusChanged)

    try {
      for (source in this.sources) {
        try {
          when (val result = source.load(
            context = this.context,
            includeTestingLibraries = includeTestingLibraries,
            useCache = useCache)) {
            is AccountProviderSourceType.SourceResult.SourceSucceeded -> {
              val newDescriptions = result.results
              for (key in newDescriptions.keys) {
                this.updateDescription(newDescriptions[key]!!)
              }
            }

            is AccountProviderSourceType.SourceResult.SourceFailed -> {
              this.eventsActual.onNext(SourceFailed(source.javaClass, result.exception))
            }
          }
        } catch (e: Exception) {
          this.eventsActual.onNext(SourceFailed(source.javaClass, e))
        }
      }
    } finally {
      this.initialized = true
      this.statusAttributeActual.set(Idle)
      this.eventsActual.onNext(StatusChanged)
    }
  }

  override fun refreshAsync(
    includeTestingLibraries: Boolean,
    useCache: Boolean
  ): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    try {
      this.exec.execute {
        try {
          future.complete(this.refresh(
            includeTestingLibraries = includeTestingLibraries,
            useCache = useCache
          ))
        } catch (e: Throwable) {
          future.completeExceptionally(e)
        }
      }
    } catch (e: Throwable) {
      future.completeExceptionally(e)
    }
    return future
  }

  override fun query(query: AccountSearchQuery) {
    this.logger.debug("refreshing account provider descriptions")

    this.statusAttributeActual.set(Refreshing)
    this.eventsActual.onNext(StatusChanged)

    try {
      for (source in this.sources) {
        try {
          when (val result = source.query(this.context, query)) {
            is AccountProviderSourceType.SourceResult.SourceSucceeded -> {
              val newDescriptions = result.results
              for (key in newDescriptions.keys) {
                this.updateDescription(newDescriptions[key]!!)
              }
            }

            is AccountProviderSourceType.SourceResult.SourceFailed -> {
              this.eventsActual.onNext(SourceFailed(source.javaClass, result.exception))
            }
          }
        } catch (e: Exception) {
          this.eventsActual.onNext(SourceFailed(source.javaClass, e))
        }
      }
    } finally {
      this.initialized = true
      this.statusAttributeActual.set(Idle)
      this.eventsActual.onNext(StatusChanged)
    }
  }

  override fun clear() {
    this.accountProviderDescriptionsAttributeActual.set(mapOf())
    this.resolved.clear()
    for (source in this.sources) {
      source.clear(this.context)
    }
  }

  override fun updateProvider(accountProvider: AccountProviderType): AccountProviderType {
    val id = accountProvider.id
    val existing = this.resolved[id]
    if (existing != null) {
      Preconditions.checkState(
        id == existing.id,
        "ID $id must match existing id ${existing.id}"
      )
      if (existing.updated.isAfter(accountProvider.updated)) {
        return existing
      }
    }

    this.logger.debug("received updated version of resolved provider {}", id)
    this.resolved[id] = accountProvider
    this.eventsActual.onNext(Updated(id))

    this.updateDescription(accountProvider.toDescription())
    return accountProvider
  }

  override fun updateDescription(
    description: AccountProviderDescription
  ): AccountProviderDescription {
    val id = description.id
    val descriptions = this.accountProviderDescriptionsAttributeActual.get()
    val existing = descriptions[id]
    if (existing != null) {
      Preconditions.checkState(
        id == existing.id,
        "ID $id must match existing id ${existing.id}"
      )
      if (existing.updated.isAfter(description.updated)) {
        return existing
      }
    }

    this.logger.debug("received updated version of description {}", id)
    this.accountProviderDescriptionsAttributeActual.set(descriptions.plus(Pair(id, description)))
    this.eventsActual.onNext(Updated(id))
    return description
  }

  override fun resolve(
    onProgress: AccountProviderResolutionListenerType,
    description: AccountProviderDescription
  ): TaskResult<AccountProviderType> {
    val taskRecorder = TaskRecorder.create()
    taskRecorder.beginNewStep("Resolving description...")

    try {
      for (source in this.sources) {
        this.logger.debug("checking source {}", source::class.java.canonicalName)
        if (source.canResolve(description)) {
          val result = source.resolve(onProgress, description)
          taskRecorder.addAll(result.steps)
          return when (result) {
            is TaskResult.Success -> {
              this.updateProvider(result.result)
              this.updateDescription(result.result.toDescription())
              taskRecorder.finishSuccess(result.result)
            }

            is TaskResult.Failure -> taskRecorder.finishFailure()
          }
        }
      }

      taskRecorder.currentStepFailed(
        message = "No sources can resolve the given description.",
        errorCode = "noApplicableSource ${description.id} ${description.title}",
        extraMessages = listOf()
      )
      return taskRecorder.finishFailure()
    } catch (e: Exception) {
      this.logger.debug("resolution exception: ", e)
      val message = e.message ?: e.javaClass.canonicalName ?: "unknown"
      taskRecorder.currentStepFailedAppending(
        message = message,
        errorCode = "unexpectedException",
        exception = e,
        extraMessages = listOf()
      )
      return taskRecorder.finishFailure()
    }
  }

  override fun close() {
    this.resources.close()
  }

  companion object {

    /**
     * Create a new description registry based on sources discovered by [ServiceLoader]
     */

    fun createFromServiceLoader(
      context: Context,
      http: LSHTTPClientType,
      defaultProvider: AccountProviderType
    ): AccountProviderRegistryType {
      val loader =
        ServiceLoader.load(AccountProviderSourceFactoryType::class.java)
      val buildConfig =
        ServiceLoader.load(BuildConfigurationServiceType::class.java).first()
      val sources =
        loader.toList().map { it.create(context, http, buildConfig) }
      return this.createFrom(context, sources, defaultProvider)
    }

    /**
     * Create a new description registry based on the given list of sources.
     */

    fun createFrom(
      context: Context,
      sources: List<AccountProviderSourceType>,
      defaultProvider: AccountProviderType
    ): AccountProviderRegistry = AccountProviderRegistry(context, sources, defaultProvider)
  }
}
