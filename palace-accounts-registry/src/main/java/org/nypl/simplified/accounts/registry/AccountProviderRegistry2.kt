package org.nypl.simplified.accounts.registry

import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountProviderDescriptionComparator
import org.nypl.simplified.accounts.api.AccountProviderResolutionListenerType
import org.nypl.simplified.accounts.api.AccountProviderResolutionStringsType
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryDebugging
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent.StatusChanged
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent.Updated
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryRefresh
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus.Failed
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus.Idle
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus.Refreshing
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.links.Links.wpmLinkToPalaceLink
import org.nypl.simplified.opds.auth_document.api.AuthenticationDocumentParsersType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
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
import org.thepalaceproject.webpub.core.WPMCatalog
import org.thepalaceproject.webpub.core.WPMManifest
import org.thepalaceproject.webpub.core.WPMMappers
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

class AccountProviderRegistry2 private constructor(
  private val accountProviderDefault: AttributeType<AccountProvider>,
  private val accountProviderDescriptionsAttributeSortedUI: AttributeType<List<AccountProviderDescription>>,
  private val accountProviderDescriptionsAttributeSrc: AttributeType<Map<URI, AccountProviderDescription>>,
  private val accountProviderDescriptionsAttributeUI: AttributeType<Map<URI, AccountProviderDescription>>,
  private val accountProviderResolutionStrings: AccountProviderResolutionStringsType,
  private val accountProvidersAttributeSrc: AttributeType<Map<URI, AccountProvider>>,
  private val accountProvidersAttributeUI: AttributeType<Map<URI, AccountProvider>>,
  private val attributeExecutor: Executor,
  private val authDocumentParsers: AuthenticationDocumentParsersType,
  private val database: DBType,
  private val databaseExecutor: ExecutorService,
  private val httpClient: LSHTTPClientType,
  private val statusAttributeSrc: AttributeType<AccountProviderRegistryStatus>,
  private val statusAttributeUI: AttributeType<AccountProviderRegistryStatus>,
  private val uriBase: URI,
) : AccountProviderRegistryType {

  private val logger =
    LoggerFactory.getLogger(AccountProviderRegistry2::class.java)

  private val eventsActual: PublishSubject<AccountProviderRegistryEvent> =
    PublishSubject.create()

  private val wpmMapper =
    WPMMappers.createMapper()

  companion object {

    private const val MAXIMUM_PROVIDER_DESCRIPTIONS = 10_000

    private val logger =
      LoggerFactory.getLogger(AccountProviderRegistry2::class.java)
    private val attributes =
      Attributes.create { ex -> this.logger.error("Uncaught exception in attribute: ", ex) }

    fun create(
      buildConfig: BuildConfigurationServiceType,
      database: DBType,
      defaultProvider: AccountProvider,
      attributeExecutor: Executor,
      databaseExecutor: ExecutorService,
      httpClient: LSHTTPClientType,
      uriBase: URI,
      accountProviderResolutionStrings: AccountProviderResolutionStringsType,
      authDocumentParsers: AuthenticationDocumentParsersType,
      uiExecutor: Executor
    ): AccountProviderRegistryType {
      val accountProviderDefault =
        this.attributes.withValue<AccountProvider>(defaultProvider)

      val accountProviderDescriptionsAttributeSrc =
        this.attributes.withValue<Map<URI, AccountProviderDescription>>(mapOf())
      val accountProviderDescriptionsAttributeUI =
        this.attributes.withValue<Map<URI, AccountProviderDescription>>(mapOf())
      val accountProvidersDescriptionsSortedAttributeUI =
        this.attributes.withValue<List<AccountProviderDescription>>(listOf())

      val comparator = AccountProviderDescriptionComparator(buildConfig)
      accountProviderDescriptionsAttributeSrc.subscribe { _, m ->
        val sorted = m.values.sortedWith(comparator)
        uiExecutor.execute {
          accountProviderDescriptionsAttributeUI.set(m)
          accountProvidersDescriptionsSortedAttributeUI.set(sorted)
        }
      }

      val accountProvidersAttributeSrc =
        this.attributes.withValue<Map<URI, AccountProvider>>(mapOf())
      val accountProvidersAttributeUI =
        this.attributes.withValue<Map<URI, AccountProvider>>(mapOf())

      accountProvidersAttributeSrc.subscribe { _, m ->
        uiExecutor.execute {
          accountProvidersAttributeUI.set(m)
        }
      }

      val statusAttributeSrc =
        this.attributes.withValue<AccountProviderRegistryStatus>(Idle)
      val statusAttributeUI =
        this.attributes.withValue<AccountProviderRegistryStatus>(Idle)

      statusAttributeSrc.subscribe { _, m ->
        uiExecutor.execute {
          statusAttributeUI.set(m)
        }
      }

      val registry =
        AccountProviderRegistry2(
          accountProviderDefault = accountProviderDefault,
          accountProviderDescriptionsAttributeSrc = accountProviderDescriptionsAttributeSrc,
          accountProviderDescriptionsAttributeSortedUI = accountProvidersDescriptionsSortedAttributeUI,
          accountProviderDescriptionsAttributeUI = accountProviderDescriptionsAttributeUI,
          accountProvidersAttributeSrc = accountProvidersAttributeSrc,
          accountProvidersAttributeUI = accountProvidersAttributeUI,
          accountProviderResolutionStrings = accountProviderResolutionStrings,
          attributeExecutor = attributeExecutor,
          authDocumentParsers = authDocumentParsers,
          database = database,
          databaseExecutor = databaseExecutor,
          httpClient = httpClient,
          statusAttributeSrc = statusAttributeSrc,
          statusAttributeUI = statusAttributeUI,
          uriBase = uriBase,
        )

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

  private fun opLoadDescriptions() {
    this.logger.debug("Loading account provider descriptions...")
    this.setStatusRefreshing(0, null)

    try {
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

        /* If the default provider description isn't in the database, we'll need to insert it now. */
        val defaultProviderDescription = this.defaultProvider.toDescription()
        if (!descriptionMap.containsKey(defaultProviderDescription.id)) {
          descriptionMap[defaultProviderDescription.id] = defaultProviderDescription
          t.execute(
            queryType = DBQAccountProviderDescriptionPutType::class.java,
            parameters = listOf(defaultProviderDescription)
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
    } finally {
      this.setStatus(Idle)
      this.eventsActual.onNext(StatusChanged)
    }
  }

  @Deprecated("Use the status attribute instead.")
  override val events: Observable<AccountProviderRegistryEvent> =
    this.eventsActual

  override val resolvedProviders: Map<URI, AccountProviderType>
    get() = this.accountProvidersAttributeUI.get()

  override val statusAttribute: AttributeReadableType<AccountProviderRegistryStatus> =
    this.statusAttributeUI

  override val accountProviderDescriptionsAttribute: AttributeReadableType<Map<URI, AccountProviderDescription>> =
    this.accountProviderDescriptionsAttributeUI

  override val accountProviderDescriptionsSortedAttribute: AttributeReadableType<List<AccountProviderDescription>> =
    this.accountProviderDescriptionsAttributeSortedUI

  override fun loadAsync(): CompletableFuture<Unit> {
    return this.execute {
      this.opLoadProviders()
      this.opLoadDescriptions()
    }
  }

  override val defaultProvider: AccountProvider =
    this.accountProviderDefault.get()

  private fun opRefresh(
    refreshRequest: AccountProviderRegistryRefresh
  ) {
    if (this.status is Refreshing) {
      this.logger.debug("Ignoring redundant refresh request.")
      return
    }

    this.logger.debug("Refreshing account provider descriptions.")
    this.setStatusRefreshing(0, null)
    var failed = false
    val taskRecorder = TaskRecorder.create()

    try {
      taskRecorder.beginNewStep("Refreshing registry...")

      if (refreshRequest.clearBeforeRefresh) {
        taskRecorder.beginNewStep("Clearing registry...")
        this.opClear()
        taskRecorder.currentStepSucceeded("Registry cleared.")
      }

      taskRecorder.beginNewStep("Adding default provider...")
      this.opProcessAccountProviderDescription(this.defaultProvider.toDescription())

      taskRecorder.beginNewStep("Fetching registry pages...")
      val idsUpdated = mutableSetOf<URI>()
      idsUpdated.add(this.defaultProvider.id)

      var totalItems: Int? = null
      var offset = 0
      val size = 113
      val baseURI = this.decideRegistryURI()
      val availability =
        if (refreshRequest.includeTestingLibraries) {
          "all"
        } else {
          "production"
        }

      while (true) {
        this.setStatusRefreshing(offset, totalItems)

        val manifest =
          this.fetchRegistryPage(
            taskRecorder = taskRecorder,
            baseURI = baseURI,
            offset = offset,
            size = size,
            availability = availability
          )

        totalItems = manifest.metadata.numberOfItems?.toInt()
        if (manifest.catalogs.isEmpty()) {
          break
        }

        for (catalog in manifest.catalogs) {
          val identifier = catalog.metadata.identifier
          if (identifier == null) {
            this.logger.warn("Catalog '{}' has no identifier", catalog.metadata.title)
            continue
          }

          this.opProcessCatalog(catalog)
          idsUpdated.add(identifier)
        }

        offset += manifest.catalogs.size
      }

      /*
       * Forget about any account provider descriptions that did not appear in the registry.
       */

      val idsExisting = this.opFindExistingIDs()
      this.logger.debug("Found {} existing account provider description IDs.", idsExisting.size)
      val notUpdated = idsExisting.minus(idsUpdated)
      this.opDeleteProviderDescriptions(notUpdated)
    } catch (e: Throwable) {
      taskRecorder.currentStepFailed(
        message = e.message ?: e.javaClass.name,
        errorCode = "exception",
        exception = e,
        extraMessages = listOf()
      )

      val result: TaskResult.Failure<WPMManifest> = taskRecorder.finishFailure()
      failed = true
      this.setStatusFailed(result)
      return
    } finally {
      if (!failed) {
        this.setStatus(Idle)
        this.eventsActual.onNext(StatusChanged)
      }
    }
  }

  private fun setStatusFailed(
    result: TaskResult.Failure<WPMManifest>
  ) {
    this.setStatus(Failed(result))
    this.eventsActual.onNext(StatusChanged)
  }

  private fun setStatusRefreshing(
    offset: Int,
    totalItems: Int?
  ) {
    if (totalItems == null) {
      this.setStatus(Refreshing(null))
      this.eventsActual.onNext(StatusChanged)
      return
    }

    this.setStatus(Refreshing(offset.toDouble() / totalItems.toDouble()))
    this.eventsActual.onNext(StatusChanged)
  }

  private fun opProcessCatalog(
    catalog: WPMCatalog
  ) {
    val accountProviderDescription: AccountProviderDescription =
      this.opCatalogToAccountProviderDescription(catalog)

    this.opProcessAccountProviderDescription(accountProviderDescription)
  }

  private fun opProcessAccountProviderDescription(
    accountProviderDescription: AccountProviderDescription
  ) {
    this.database.openTransaction().use { t ->
      t.execute(
        queryType = DBQAccountProviderDescriptionPutType::class.java,
        parameters = listOf(accountProviderDescription)
      )
      t.commit()
    }

    val existing = this.accountProviderDescriptionsAttributeSrc.get()
    val withNew = existing.plus(Pair(accountProviderDescription.id, accountProviderDescription))
    this.accountProviderDescriptionsAttributeSrc.set(withNew)
  }

  private fun opCatalogToAccountProviderDescription(
    catalog: WPMCatalog
  ): AccountProviderDescription {
    val title =
      catalog.metadata.title.defaultValue
    val identifier =
      catalog.metadata.identifier!!
    val updated =
      catalog.metadata.modified ?: OffsetDateTime.now()
    val links =
      catalog.links.map(::wpmLinkToPalaceLink)
    val images =
      catalog.images.map(::wpmLinkToPalaceLink)

    return AccountProviderDescription(
      id = identifier,
      title = title,
      description = catalog.metadata.description,
      updated = updated,
      links = links,
      images = images
    )
  }

  private fun fetchRegistryPage(
    taskRecorder: TaskRecorderType,
    baseURI: URI,
    offset: Int,
    size: Int,
    availability: String
  ): WPMManifest {
    val targetURI =
      URI.create("$baseURI/libraries/crawlable?offset=$offset&size=$size&availability=$availability")

    for (attempt in 1..3) {
      taskRecorder.beginNewStep("Fetching $targetURI (Attempt $attempt of 3)")

      val request =
        this.httpClient.newRequest(targetURI)
          .build()
      val response =
        request.execute()

      return when (val status = response.status) {
        is LSHTTPResponseStatus.Failed -> {
          Thread.sleep(1_000L)
          status.properties?.let { p -> taskRecorder.addPropertiesAsAttributes(p) }
          taskRecorder.currentStepFailed(
            message = "Failed to connect to registry.",
            errorCode = "http-failed",
            exception = status.exception,
            extraMessages = listOf()
          )
          continue
        }

        is LSHTTPResponseStatus.Responded.Error -> {
          Thread.sleep(1_000L)
          taskRecorder.addPropertiesAsAttributes(status.properties)
          taskRecorder.currentStepFailed(
            message = "Registry returned an error.",
            errorCode = "registry-error",
            exception = null,
            extraMessages = listOf()
          )
          continue
        }

        is LSHTTPResponseStatus.Responded.OK -> {
          taskRecorder.addPropertiesAsAttributes(status.properties)
          try {
            this.wpmMapper.readValue(
              status.bodyStream ?: ByteArrayInputStream(ByteArray(0)),
              WPMManifest::class.java
            )
          } catch (e: Exception) {
            taskRecorder.currentStepFailed(
              message = e.message ?: e.javaClass.name,
              errorCode = "json-parsing",
              exception = e,
              extraMessages = listOf()
            )
            continue
          }
        }
      }
    }

    throw IOException("Failed to retrieve a registry page after multiple attempts.")
  }

  private fun setStatus(
    status: AccountProviderRegistryStatus
  ) {
    this.logger.debug("setStatus: {}", status)
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
    refreshRequest: AccountProviderRegistryRefresh
  ): CompletableFuture<Unit> {
    return this.execute {
      this.opRefresh(refreshRequest)
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
        parameters = listOf(this.defaultProvider.toDescription())
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
    this.opUpdateDescriptions(listOf(accountProvider.toDescription()))
    return accountProvider
  }

  private fun opUpdateDescriptions(
    descriptions: List<AccountProviderDescription>
  ): List<AccountProviderDescription> {
    this.database.openTransaction().use { t ->

      val timeThen = System.nanoTime()
      t.execute(
        queryType = DBQAccountProviderDescriptionPutType::class.java,
        parameters = descriptions
      )
      t.commit()
      val timeNow = System.nanoTime()
      val timeDiff = (timeNow - timeThen).toDouble() / 1_000_000.0
      this.logger.debug("Stored {} provider descriptions in {} ms", descriptions.size, timeDiff)

      this.attributeExecutor.execute {
        val srcS = this.accountProviderDescriptionsAttributeSrc.get()
        val incM = descriptions.associateBy { description -> description.id }
        val srcR = srcS.plus(incM)
        this.accountProviderDescriptionsAttributeSrc.set(srcR)
      }

      for (description in descriptions) {
        this.eventsActual.onNext(Updated(description.id))
      }
    }
    return descriptions
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
    return this.execute { this.opUpdateDescriptions(listOf(description)).first() }
  }

  override fun updateDescriptionsAsync(
    descriptions: List<AccountProviderDescription>
  ): CompletableFuture<List<AccountProviderDescription>> {
    return this.execute { this.opUpdateDescriptions(descriptions) }
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
      val resolution =
        AccountProviderResolution(
          stringResources = this.accountProviderResolutionStrings,
          authDocumentParsers = this.authDocumentParsers,
          http = this.httpClient,
          description = description
        )

      return when (val result = resolution.resolve(onProgress)) {
        is TaskResult.Failure<AccountProviderType> -> {
          taskRecorder.finishFailure()
        }

        is TaskResult.Success<AccountProviderType> -> {
          this.opUpdateProvider(result.result)
          taskRecorder.finishSuccess(result.result)
        }
      }
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

  private fun decideRegistryURI(): URI {
    val debuggingBase =
      AccountProviderRegistryDebugging.properties[
        "org.nypl.simplified.accounts.source.nyplregistry.baseServerOverride"
      ]
    return if (debuggingBase != null) {
      URI.create("https://$debuggingBase/libraries")
    } else {
      this.uriBase
    }
  }
}
