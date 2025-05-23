package org.thepalaceproject.opds.client

import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedAuthentication
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedGeneral
import org.nypl.simplified.presentableerror.api.PresentableErrorType
import org.slf4j.LoggerFactory
import org.thepalaceproject.opds.client.OPDSClientRequest.HistoryBehavior.ADD_TO_HISTORY
import org.thepalaceproject.opds.client.OPDSClientRequest.HistoryBehavior.CLEAR_HISTORY
import org.thepalaceproject.opds.client.OPDSClientRequest.HistoryBehavior.REPLACE_TIP
import org.thepalaceproject.opds.client.OPDSFeedHandleWithoutGroupsType.Page
import org.thepalaceproject.opds.client.OPDSState.Error
import org.thepalaceproject.opds.client.OPDSState.Initial
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedEntry
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedWithGroups
import org.thepalaceproject.opds.client.OPDSState.LoadedFeedWithoutGroups
import org.thepalaceproject.opds.client.OPDSState.Loading
import java.io.IOException
import java.net.URI
import java.util.SortedMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class OPDSClient private constructor(
  private val parameters: OPDSClientParameters
) : OPDSClientType {

  private var topmostHandlerSubscriptions: CloseableCollectionType<ClosingResourceFailedException> =
    CloseableCollection.create()

  private val logger =
    LoggerFactory.getLogger(OPDSClient::class.java)

  private val closed =
    AtomicBoolean(false)

  private val taskExecutor: ExecutorService =
    Executors.newCachedThreadPool { r ->
      val thread = Thread(r)
      thread.name = "org.thepalaceproject.opds.client.${this.parameters.name}"
      thread.priority = Thread.MIN_PRIORITY
      thread
    }

  private val feedEntryCorrupt =
    FeedEntry.FeedEntryCorrupt(
      AccountID.generate(),
      BookIDs.newFromText("urn:uuid:2688faf3-1927-4245-a1d9-7824bae1a49f"),
      IllegalStateException("Nonexistent book!")
    )

  private val resources: CloseableCollectionType<ClosingResourceFailedException> =
    CloseableCollection.create()

  private val stateSource: AttributeType<OPDSState> =
    OPDSClientAttributes.attributes.withValue(Initial)
  private val entriesGroupedSource: AttributeType<List<FeedGroup>> =
    OPDSClientAttributes.attributes.withValue(listOf())
  private val entrySource: AttributeType<FeedEntry> =
    OPDSClientAttributes.attributes.withValue(this.feedEntryCorrupt)

  private val stateUI: AttributeType<OPDSState> =
    OPDSClientAttributes.attributes.withValue(Initial)
  private val entriesGroupedUI: AttributeType<List<FeedGroup>> =
    OPDSClientAttributes.attributes.withValue(listOf())
  private val entryUI: AttributeType<FeedEntry> =
    OPDSClientAttributes.attributes.withValue(this.feedEntryCorrupt)

  private val stateUISub =
    this.resources.add(
      this.stateSource.subscribe { _, newValue ->
        this.parameters.runOnUI(Runnable { this.stateUI.set(newValue) })
      }
    )
  private val entriesGroupedUISub =
    this.resources.add(
      this.entriesGroupedSource.subscribe { _, e ->
        this.parameters.runOnUI(Runnable { this.entriesGroupedUI.set(e) })
      }
    )
  private val entryUISub =
    this.resources.add(
      this.entrySource.subscribe { _, newValue ->
        this.parameters.runOnUI(Runnable { this.entryUI.set(newValue) })
      }
    )

  private val requestStack: ConcurrentLinkedDeque<RequestHandler> =
    ConcurrentLinkedDeque()

  init {
    /*
     * Note: Resources are essentially pushed onto a stack and are therefore closed in reverse order. Therefore,
     * the order of resource registrations is significant: The last resource to be pushed will be closed _first_.
     */
    this.resources.add(AutoCloseable {
      this.taskExecutor.awaitTermination(5L, TimeUnit.SECONDS)
    })
    this.resources.add(AutoCloseable {
      this.taskExecutor.shutdown()
    })
    this.resources.add(AutoCloseable {
      this.requestStack.forEach(RequestHandler::close)
    })
  }

  companion object {
    fun create(
      parameters: OPDSClientParameters
    ): OPDSClientType {
      return OPDSClient(parameters)
    }
  }

  override val state: AttributeReadableType<OPDSState> =
    this.stateUI
  override val entriesGrouped: AttributeReadableType<List<FeedGroup>> =
    this.entriesGroupedUI
  override val entry: AttributeReadableType<FeedEntry> =
    this.entryUI

  /**
   * A request handler is created for each incoming client request. The system maintains a
   * stack of requests and subscribes to (and re-publishes) the state of the topmost handler.
   */

  private inner class RequestHandler(
    val request: OPDSClientRequest
  ) : Runnable, AutoCloseable {
    val state: AttributeType<OPDSState> =
      OPDSClientAttributes.attributes.withValue(Loading(this.request))

    @Volatile
    private var feedURINext: URI? =
      this.request.uri

    private inner class OPDSFeedHandleSingleEntry : OPDSFeedHandleSingleEntryType {
      @Volatile
      lateinit var entry: FeedEntry
    }

    private inner class OPDSFeedHandleWithGroups : OPDSFeedHandleWithGroupsType {
      @Volatile
      lateinit var feed: Feed.FeedWithGroups

      override fun feed(): Feed.FeedWithGroups {
        return this.feed
      }

      override fun refresh(): CompletableFuture<Unit> {
        return this@OPDSClient.executeWithFuture(this@RequestHandler)
      }
    }

    private inner class OPDSFeedHandleWithoutGroups : OPDSFeedHandleWithoutGroupsType {

      @Volatile
      private var positionSaved: Int = 0

      @Volatile
      var credentials: AccountAuthenticationCredentials? = null

      @Volatile
      lateinit var method: String

      @Volatile
      lateinit var feedInitial: Feed.FeedWithoutGroups

      val pages: SortedMap<Int, Page> =
        ConcurrentSkipListMap()

      override fun pages(): Int {
        return this.pages.size
      }

      override fun page(
        index: Int
      ): CompletableFuture<Page> {
        val pageFuture = CompletableFuture<Page>()
        val page = this.pages[index]
        if (page != null) {
          pageFuture.complete(page)
          return pageFuture
        }

        val previous = this.pages[index - 1]
        if (previous == null) {
          pageFuture.completeExceptionally(IOException("No previous page (${index - 1})"))
          return pageFuture
        }

        val previousNext = previous.data.feedNext
        if (previousNext == null) {
          pageFuture.completeExceptionally(IOException("No next page (${index - 1})"))
          return pageFuture
        }

        val future0 =
          this@OPDSClient.parameters.feedLoader.fetchURI(
            accountID = this@RequestHandler.request.accountID,
            uri = previousNext,
            credentials = this.credentials,
            method = this.method
          )

        future0.whenComplete { loaderResult, exception ->
          if (exception != null) {
            pageFuture.completeExceptionally(exception)
            return@whenComplete
          }

          when (loaderResult) {
            is FeedLoaderFailedAuthentication -> {
              pageFuture.completeExceptionally(loaderResult.exception)
            }

            is FeedLoaderFailedGeneral -> {
              pageFuture.completeExceptionally(loaderResult.exception)
            }

            is FeedLoaderResult.FeedLoaderSuccess -> {
              when (val feed = loaderResult.feed) {
                is Feed.FeedWithGroups -> {
                  pageFuture.completeExceptionally(
                    IOException(
                      "Received a feed with groups, but was expecting a feed without groups ($previousNext)"
                    )
                  )
                }

                is Feed.FeedWithoutGroups -> {
                  val newPage = Page(
                    pageIndex = index,
                    pagePrevious = if (index > 0) index - 1 else null,
                    pageNext = if (feed.feedNext != null) index + 1 else null,
                    data = feed
                  )
                  this.pages[index] = newPage
                  pageFuture.complete(newPage)
                }
              }
            }
          }
        }
        return pageFuture
      }

      override fun feed(): Feed.FeedWithoutGroups {
        return this.feedInitial
      }

      override fun scrollPositionSave(position: Int) {
        this.positionSaved = position
      }

      override fun scrollPositionGet(): Int {
        return this.positionSaved
      }

      override fun refresh(): CompletableFuture<Unit> {
        val handler = this@RequestHandler
        handler.feedURINext = handler.request.uri
        handler.publishedEntriesGrouped.set(listOf())
        this.positionSaved = 0
        return this@OPDSClient.executeWithFuture(handler)
      }
    }

    private val handleEntry: OPDSFeedHandleSingleEntry =
      this.OPDSFeedHandleSingleEntry()
    private val handleUngrouped: OPDSFeedHandleWithoutGroups =
      this.OPDSFeedHandleWithoutGroups()
    private val handleGrouped: OPDSFeedHandleWithGroups =
      this.OPDSFeedHandleWithGroups()

    val publishedEntriesGrouped: AttributeType<List<FeedGroup>> =
      OPDSClientAttributes.attributes.withValue(listOf())

    private val closed =
      AtomicBoolean(false)

    override fun run() {
      try {
        if (this.closed.get()) {
          return
        }
        this.runActual()
      } catch (e: Throwable) {
        this@OPDSClient.logger.warn("Task failure: ", e)
        throw e
      }
    }

    private fun runActual() {
      return when (this.request) {
        is OPDSClientRequest.ExistingEntry -> {
          this.runExistingEntry(this.request)
        }

        is OPDSClientRequest.GeneratedFeed -> {
          this.runGeneratedFeed(this.request)
        }

        is OPDSClientRequest.NewFeed -> {
          this.runRemoteFeed(this.request)
        }
      }
    }

    private fun runRemoteFeed(
      request: OPDSClientRequest.NewFeed
    ) {
      val future0 =
        this@OPDSClient.parameters.feedLoader.fetchURI(
          accountID = request.accountID,
          uri = request.uri,
          credentials = request.credentials,
          method = request.method
        )

      when (val result = future0.get()) {
        is FeedLoaderFailedAuthentication -> {
          this.state.set(
            Error(
              message = result,
              request = request
            )
          )
        }

        is FeedLoaderFailedGeneral -> {
          this.state.set(
            Error(
              message = result,
              request = request
            )
          )
        }

        is FeedLoaderResult.FeedLoaderSuccess -> {
          when (val feed = result.feed) {
            is Feed.FeedWithGroups -> {
              this.handleGrouped.feed = feed
              this.state.set(LoadedFeedWithGroups(request, this.handleGrouped))
              this.publishedEntriesGrouped.set(feed.feedGroupsInOrder)
              this.feedURINext = feed.feedNext
            }

            is Feed.FeedWithoutGroups -> {
              this.handleUngrouped.feedInitial = feed
              this.handleUngrouped.credentials = request.credentials
              this.handleUngrouped.method = request.method
              this.handleUngrouped.pages.clear()
              this.handleUngrouped.pages[0] = Page(
                pageIndex = 0,
                pagePrevious = null,
                pageNext = if (feed.feedNext != null) 1 else null,
                data = feed
              )
              this.state.set(LoadedFeedWithoutGroups(request, this.handleUngrouped))
              this.publishedEntriesGrouped.set(listOf())
            }
          }
        }
      }
    }

    private fun runGeneratedFeed(
      request: OPDSClientRequest.GeneratedFeed
    ) {
      try {
        when (val feed = request.generator.invoke()) {
          is Feed.FeedWithGroups -> {
            this.handleGrouped.feed = feed
            this.state.set(LoadedFeedWithGroups(request, this.handleGrouped))
            this.publishedEntriesGrouped.set(feed.feedGroupsInOrder)
            this.feedURINext = feed.feedNext
          }

          is Feed.FeedWithoutGroups -> {
            this.handleUngrouped.feedInitial = feed
            this.handleUngrouped.credentials = null
            this.handleUngrouped.method = "GET"
            this.handleUngrouped.pages[0] = Page(
              pageIndex = 0,
              pagePrevious = null,
              pageNext = null,
              data = feed
            )
            this.state.set(LoadedFeedWithoutGroups(request, this.handleUngrouped))
            this.publishedEntriesGrouped.set(listOf())
          }
        }
      } catch (e: Throwable) {
        this.state.set(
          Error(
            message = this@OPDSClient.generatedFeedException(request, e),
            request = request
          )
        )
        throw e
      }
    }

    private fun runExistingEntry(
      request: OPDSClientRequest.ExistingEntry
    ) {
      this.handleEntry.entry = request.entry

      this.state.set(
        LoadedFeedEntry(
          request = request,
          handle = this.handleEntry
        )
      )
    }

    override fun close() {
      if (this.closed.compareAndSet(false, true)) {
        // Nothing
      }
    }
  }

  private fun generatedFeedException(
    request: OPDSClientRequest.GeneratedFeed,
    throwable: Throwable
  ): PresentableErrorType {
    return FeedLoaderFailedGeneral(
      problemReport = null,
      exception = Exception(throwable),
      message = throwable.message ?: "Unexpected error occurred.",
      attributesInitial = mapOf(
        Pair("AccountID", request.accountID.toString())
      )
    )
  }

  override val hasHistory: Boolean
    get() = this.requestStack.size > 1

  override fun goBack(): CompletableFuture<Unit> {
    this.parameters.checkOnUI()

    val f = this.checkClosed()
    if (f != null) {
      return f
    }

    if (this.requestStack.isEmpty()) {
      return CompletableFuture.completedFuture(Unit)
    }

    this.requestPop()
    return CompletableFuture.completedFuture(Unit)
  }

  override fun goTo(
    request: OPDSClientRequest
  ): CompletableFuture<Unit> {
    this.parameters.checkOnUI()

    val f = this.checkClosed()
    if (f != null) {
      return f
    }

    return when (request.historyBehavior) {
      ADD_TO_HISTORY -> {
        this.requestPush(this.RequestHandler(request))
      }

      REPLACE_TIP -> {
        this.requestPushReplacingTip(this.RequestHandler(request))
      }

      CLEAR_HISTORY -> {
        this.requestStack.clear()
        this.requestPush(this.RequestHandler(request))
      }
    }
  }

  override fun clearHistory() {
    this.parameters.checkOnUI()

    val f = this.checkClosed()
    if (f != null) {
      return
    }

    this.topmostHandlerSubscriptions.close()
    this.topmostHandlerSubscriptions = CloseableCollection.create()
    this.requestStack.clear()
    this.stateSource.set(Initial)
  }

  private fun requestPop() {
    if (this.requestStack.size < 2) {
      return
    }

    this.topmostHandlerSubscriptions.close()
    this.topmostHandlerSubscriptions = CloseableCollection.create()

    this.requestStack.pop()

    val newTopmost = this.requestStack.peek()!!
    this.topmostHandlerSubscriptions.add(
      newTopmost.state.subscribe { _, newState ->
        this.onHandlerPublishedStateUpdate(newTopmost, newState)
      }
    )
    this.traceStack()
  }

  private fun traceStack() {
    this.logger.trace("Stack is now:")
    this.requestStack.forEachIndexed { index, handler ->
      this.logger.trace("  Stack [{}]: {}", index, handler.request.uri)
    }
  }

  private fun requestPushReplacingTip(
    handler: RequestHandler
  ): CompletableFuture<Unit> {
    this.topmostHandlerSubscriptions.close()
    this.topmostHandlerSubscriptions = CloseableCollection.create()

    this.requestStack.removeFirst()
    this.requestStack.push(handler)

    this.topmostHandlerSubscriptions.add(
      handler.state.subscribe { _, newState ->
        this.onHandlerPublishedStateUpdate(handler, newState)
      }
    )

    this.traceStack()
    return this.executeWithFuture(handler)
  }

  private fun requestPush(
    handler: RequestHandler
  ): CompletableFuture<Unit> {
    this.topmostHandlerSubscriptions.close()
    this.topmostHandlerSubscriptions = CloseableCollection.create()

    this.requestStack.push(handler)
    this.topmostHandlerSubscriptions.add(
      handler.state.subscribe { _, newState ->
        this.onHandlerPublishedStateUpdate(handler, newState)
      }
    )

    this.traceStack()
    return this.executeWithFuture(handler)
  }

  private fun executeWithFuture(
    task: Runnable
  ): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    try {
      this.taskExecutor.execute {
        try {
          future.complete(task.run())
        } catch (e: Throwable) {
          future.completeExceptionally(e)
        }
      }
    } catch (e: Throwable) {
      future.completeExceptionally(e)
    }
    return future
  }

  private fun onHandlerPublishedStateUpdate(
    handler: RequestHandler,
    newState: OPDSState
  ) {
    when (newState) {
      is Error -> {
        this.entrySource.set(this.feedEntryCorrupt)
        this.entriesGroupedSource.set(listOf())
      }

      Initial -> {
        this.entrySource.set(this.feedEntryCorrupt)
        this.entriesGroupedSource.set(listOf())
      }

      is Loading -> {
        this.entrySource.set(this.feedEntryCorrupt)
        this.entriesGroupedSource.set(listOf())
      }

      is LoadedFeedEntry -> {
        this.entrySource.set(newState.request.entry)
        this.entriesGroupedSource.set(listOf())
      }

      is LoadedFeedWithGroups -> {
        this.entrySource.set(this.feedEntryCorrupt)
        this.topmostHandlerSubscriptions.add(
          handler.publishedEntriesGrouped.subscribe { _, groups ->
            this.entriesGroupedSource.set(groups)
          }
        )
      }

      is LoadedFeedWithoutGroups -> {
        this.entrySource.set(this.feedEntryCorrupt)
        this.entriesGroupedSource.set(listOf())
      }
    }

    this.logger.trace("State: {}", newState.javaClass.simpleName)
    this.stateSource.set(newState)
  }

  private fun checkClosed(): CompletableFuture<Unit>? {
    if (this.closed.get()) {
      val future = CompletableFuture<Unit>()
      future.completeExceptionally(IllegalStateException("Client is closed."))
      return future
    }
    return null
  }

  override fun close() {
    if (this.closed.compareAndSet(false, true)) {
      this.resources.add(this.topmostHandlerSubscriptions)
      this.resources.close()
    }
  }
}
