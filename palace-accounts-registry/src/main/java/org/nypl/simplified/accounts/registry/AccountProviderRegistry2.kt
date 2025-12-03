package org.nypl.simplified.accounts.registry

import android.content.Context
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountProviderResolutionListenerType
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent.StatusChanged
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent.Updated
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus.Idle
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus.Refreshing
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceType
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceType.SourceResult.SourceFailed
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceType.SourceResult.SourceSucceeded
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionDeleteAllType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionDeleteType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionIDSetType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionListType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionPutType
import org.thepalaceproject.db.api.queries.DBQAccountProviderGetType
import org.thepalaceproject.db.api.queries.DBQAccountProviderListType
import org.thepalaceproject.db.api.queries.DBQAccountProviderPutType
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

class AccountProviderRegistry2 private constructor(
  private val accountProviderDescriptionsAttributeSrc: AttributeType<Map<URI, AccountProviderDescription>>,
  private val accountProvidersAttributeSrc: AttributeType<Map<URI, AccountProvider>>,
  private val attributeExecutor: Executor,
  private val context: Context,
  private val database: DBType,
  private val databaseExecutor: ExecutorService,
  private val sources: List<AccountProviderSourceType>,
  private val statusAttributeSrc: AttributeType<AccountProviderRegistryStatus>,
  private val accountProviderDefault: AttributeType<AccountProvider>,
) : AccountProviderRegistryType {

  private val logger =
    LoggerFactory.getLogger(AccountProviderRegistry2::class.java)

  private val eventsActual: PublishSubject<AccountProviderRegistryEvent> =
    PublishSubject.create()

  companion object {

    private const val MAXIMUM_PROVIDER_DESCRIPTIONS = 10_000

    private val logger =
      LoggerFactory.getLogger(AccountProviderRegistry2::class.java)
    private val attributes =
      Attributes.create { ex -> this.logger.error("Uncaught exception in attribute: ", ex) }

    fun create(
      context: Context,
      database: DBType,
      defaultProvider: AccountProvider,
      sources: List<AccountProviderSourceType>,
      attributeExecutor: Executor,
      databaseExecutor: ExecutorService,
    ): AccountProviderRegistryType {
      val statusAttributeSrc =
        this.attributes.withValue<AccountProviderRegistryStatus>(Idle)
      val accountProviderDescriptionsAttributeSrc =
        this.attributes.withValue<Map<URI, AccountProviderDescription>>(mapOf())
      val accountProvidersAttributeSrc =
        this.attributes.withValue<Map<URI, AccountProvider>>(mapOf())
      val accountProviderDefault =
        this.attributes.withValue<AccountProvider>(defaultProvider)

      val registry =
        AccountProviderRegistry2(
          accountProviderDefault = accountProviderDefault,
          accountProviderDescriptionsAttributeSrc = accountProviderDescriptionsAttributeSrc,
          accountProvidersAttributeSrc = accountProvidersAttributeSrc,
          attributeExecutor = attributeExecutor,
          context = context,
          database = database,
          databaseExecutor = databaseExecutor,
          sources = sources,
          statusAttributeSrc = statusAttributeSrc,
        )

      registry.loadDescriptions()
      registry.loadProviders()
      return registry
    }
  }

  private fun loadProviders() {
    return this.execute { this.opLoadProviders() }.get()
  }

  private fun opLoadProviders() {
    this.logger.debug("Loading account providers...")

    val descriptionMap: MutableMap<URI, AccountProvider>
    this.database.openTransaction().use { t ->
      val descriptions =
        t.execute(
          queryType = DBQAccountProviderListType::class.java,
          parameters = DBQAccountProviderListType.Parameters(
            startingId = null,
            limit = MAXIMUM_PROVIDER_DESCRIPTIONS
          )
        )

      /*
       * If the default provider isn't in the database, we'll need to insert it now.
       */

      descriptionMap = descriptions.associateBy { d -> d.id }.toMutableMap()
      if (!descriptionMap.containsKey(this.defaultProvider.id)) {
        descriptionMap[this.defaultProvider.id] = this.defaultProvider
        t.execute(
          queryType = DBQAccountProviderPutType::class.java,
          parameters = this.defaultProvider
        )
        t.commit()
      }
      this.attributeExecutor.execute {
        this.accountProvidersAttributeSrc.set(descriptionMap)
      }
    }

    this.logger.debug(
      "Loaded {} account providers.",
      descriptionMap.size
    )
  }

  private fun loadDescriptions() {
    return this.execute { this.opLoadDescriptions() }.get()
  }

  private fun opLoadDescriptions() {
    this.logger.debug("Loading account provider descriptions...")

    val descriptionMap: MutableMap<URI, AccountProviderDescription>
    this.database.openTransaction().use { t ->
      val descriptions =
        t.execute(
          queryType = DBQAccountProviderDescriptionListType::class.java,
          parameters = DBQAccountProviderDescriptionListType.Parameters(
            startingId = null,
            limit = MAXIMUM_PROVIDER_DESCRIPTIONS
          )
        )

      descriptionMap = descriptions.associateBy { d -> d.id }.toMutableMap()

      /*
       * If the default provider description isn't in the database, we'll need to insert it now.
       */

      val defaultProviderDescription = this.defaultProvider.toDescription()
      if (!descriptionMap.containsKey(defaultProviderDescription.id)) {
        descriptionMap[defaultProviderDescription.id] = defaultProviderDescription
        t.execute(
          queryType = DBQAccountProviderDescriptionPutType::class.java,
          parameters = defaultProviderDescription
        )
        t.commit()
      }

      this.attributeExecutor.execute {
        this.accountProviderDescriptionsAttributeSrc.set(descriptionMap)
      }
    }

    this.logger.debug(
      "Loaded {} account provider descriptions.",
      descriptionMap.size
    )
  }

  @Deprecated("Use the status attribute instead.")
  override val events: Observable<AccountProviderRegistryEvent> =
    this.eventsActual

  override val resolvedProviders: Map<URI, AccountProviderType>
    get() = this.accountProvidersAttributeSrc.get()

  override val statusAttribute: AttributeReadableType<AccountProviderRegistryStatus> =
    this.statusAttributeSrc

  override val accountProviderDescriptionsAttribute: AttributeReadableType<Map<URI, AccountProviderDescription>> =
    this.accountProviderDescriptionsAttributeSrc

  override val defaultProvider: AccountProvider =
    this.accountProviderDefault.get()

  private fun opRefresh(
    includeTestingLibraries: Boolean,
  ) {
    this.logger.debug("Refreshing account provider descriptions.")

    this.setStatus(Refreshing)
    this.eventsActual.onNext(StatusChanged)

    try {
      /*
       * Maintain a set of IDs that were updated from any source. We treat the default provider
       * as always having been updated. We'll delete any account provider descriptions that haven't
       * been updated from any source.
       */

      val idsUpdated = mutableSetOf<URI>()
      idsUpdated.add(this.defaultProvider.id)

      for (source in this.sources) {
        val data =
          source.load(
            context = this.context,
            includeTestingLibraries = includeTestingLibraries,
          )

        when (data) {
          is SourceFailed -> {
            for ((_, description) in data.results) {
              try {
                idsUpdated.add(description.id)
                this.opUpdateDescription(description)
              } catch (e: Exception) {
                this.logger.debug("Failed to update description: ", e)
              }
            }
          }

          is SourceSucceeded -> {
            for ((_, description) in data.results) {
              try {
                idsUpdated.add(description.id)
                this.opUpdateDescription(description)
              } catch (e: Exception) {
                this.logger.debug("Failed to update description: ", e)
              }
            }
          }
        }
      }

      /*
       * Forget about any account provider descriptions that did not appear in any source.
       */

      val idsExisting = this.opFindExistingIDs()
      this.logger.debug("Found {} existing account provider description IDs.", idsExisting.size)
      val notUpdated = idsExisting.minus(idsUpdated)
      this.opDeleteProviderDescriptions(notUpdated)
      this.opLoadDescriptions()
    } finally {
      this.setStatus(Idle)
      this.eventsActual.onNext(StatusChanged)
    }
  }

  private fun setStatus(
    status: AccountProviderRegistryStatus
  ) {
    this.attributeExecutor.execute { this.statusAttributeSrc.set(status) }
  }

  /**
   * Execute a task on the database executor and return a future representing the operation
   * in progress.
   */

  private fun <T> execute(
    f: () -> T
  ): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    try {
      this.databaseExecutor.execute {
        try {
          future.complete(f.invoke())
        } catch (e: Throwable) {
          this.logger.debug("Registry operation failed: ", e)
          future.completeExceptionally(e)
        }
      }
    } catch (e: Throwable) {
      this.logger.debug("Registry operation failed: ", e)
      future.completeExceptionally(e)
    }
    return future
  }

  override fun refreshAsync(
    includeTestingLibraries: Boolean,
  ): CompletableFuture<Unit> {
    return this.execute {
      this.opRefresh(
        includeTestingLibraries = includeTestingLibraries,
      )
    }
  }

  override fun clearAsync(): CompletableFuture<Unit> {
    return this.execute { this.opClear() }
  }

  private fun opClear() {
    this.database.openTransaction().use { t ->
      t.execute(
        queryType = DBQAccountProviderDescriptionDeleteAllType::class.java,
        parameters = Unit
      )
      t.execute(
        queryType = DBQAccountProviderDescriptionPutType::class.java,
        parameters = this.defaultProvider.toDescription()
      )
      t.commit()
    }

    this.attributeExecutor.execute {
      this.accountProviderDescriptionsAttributeSrc.set(mapOf())
    }
  }

  override fun accountProviderDescriptions(): Map<URI, AccountProviderDescription> {
    return this.accountProviderDescriptionsAttributeSrc.get()
  }

  override fun updateProviderAsync(
    accountProvider: AccountProviderType
  ): CompletableFuture<AccountProviderType> {
    return this.execute { this.opUpdateProvider(accountProvider) }
  }

  private fun opUpdateProvider(
    accountProviderOriginal: AccountProviderType
  ): AccountProviderType {
    val accountProvider =
      AccountProvider.copy(accountProviderOriginal)

    /*
     * If there's an account provider in the database that has the given ID, then we only
     * update the copy in the database if the incoming provider has a more recent update time.
     */

    val existing =
      this.database.openTransaction().use { t ->
        t.execute(
          queryType = DBQAccountProviderGetType::class.java,
          parameters = accountProvider.id
        )
      }

    if (existing != null) {
      check(accountProvider.id == existing.id) {
        "ID ${accountProvider.id} must match existing id ${existing.id}"
      }
      if (existing.updated.isAfter(accountProvider.updated)) {
        return existing
      }
    }

    this.logger.debug("Received updated version of resolved provider {}", accountProvider.id)
    this.database.openTransaction().use { t ->
      t.execute(
        queryType = DBQAccountProviderPutType::class.java,
        parameters = AccountProvider.copy(accountProvider)
      )
      t.commit()
    }

    if (accountProvider.id == this.defaultProvider.id) {
      this.logger.debug("Updated default provider.")
      this.accountProviderDefault.set(accountProvider)
    }

    this.attributeExecutor.execute {
      val oldM = this.accountProvidersAttributeSrc.get()
      val newM = oldM.plus(Pair(accountProvider.id, accountProvider))
      this.accountProvidersAttributeSrc.set(newM)
    }

    this.eventsActual.onNext(Updated(accountProvider.id))
    this.opUpdateDescription(accountProvider.toDescription())
    return accountProvider
  }

  private fun opUpdateDescription(
    description: AccountProviderDescription
  ): AccountProviderDescription {
    this.logger.debug("Updating description {}.", description.id)
    this.database.openTransaction().use { t ->
      t.execute(
        queryType = DBQAccountProviderDescriptionPutType::class.java,
        parameters = description
      )
      t.commit()
      this.attributeExecutor.execute {
        val srcS = this.accountProviderDescriptionsAttributeSrc.get()
        val srcR = srcS.plus(Pair(description.id, description))
        this.accountProviderDescriptionsAttributeSrc.set(srcR)
      }
      this.eventsActual.onNext(Updated(description.id))
    }
    return description
  }

  private fun opFindExistingIDs(): Set<URI> {
    return this.database.openTransaction().use { t ->
      t.execute(DBQAccountProviderDescriptionIDSetType::class.java, Unit)
    }
  }

  private fun opDeleteProviderDescriptions(
    ids: Set<URI>
  ) {
    this.logger.debug("Deleting {} obsolete account provider descriptions.", ids.size)
    return this.database.openTransaction().use { t ->
      t.execute(DBQAccountProviderDescriptionDeleteType::class.java, ids)
      t.commit()
    }
  }

  override fun updateDescriptionAsync(
    description: AccountProviderDescription
  ): CompletableFuture<AccountProviderDescription> {
    return this.execute { this.opUpdateDescription(description) }
  }

  override fun resolveAsync(
    onProgress: AccountProviderResolutionListenerType,
    description: AccountProviderDescription
  ): CompletableFuture<TaskResult<AccountProviderType>> {
    return this.execute { this.opResolve(onProgress, description) }
  }

  private fun opResolve(
    onProgress: AccountProviderResolutionListenerType,
    description: AccountProviderDescription
  ): TaskResult<AccountProviderType> {
    val taskRecorder = TaskRecorder.create()
    taskRecorder.beginNewStep("Resolving description...")

    try {
      for (source in this.sources) {
        this.logger.debug("Checking source {}", source::class.java.canonicalName)
        if (source.canResolve(description)) {
          val result = source.resolve(onProgress, description)
          taskRecorder.addAll(result.steps)
          return when (result) {
            is TaskResult.Success -> {
              this.opUpdateProvider(result.result)
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
      this.logger.debug("Resolution exception: ", e)
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
    // Nothing required.
  }
}
