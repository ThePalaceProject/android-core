package org.thepalaceproject.opds.client

import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.slf4j.LoggerFactory
import org.thepalaceproject.opds.client.OPDSState.Initial
import org.thepalaceproject.opds.client.OPDSState.OPDSStateHistoryParticipant
import org.thepalaceproject.opds.client.internal.OPDSCmd
import org.thepalaceproject.opds.client.internal.OPDSCmdContextType
import org.thepalaceproject.opds.client.internal.OPDSCmdExecuteRequest
import org.thepalaceproject.opds.client.internal.OPDSCmdLoadMore
import org.thepalaceproject.opds.client.internal.OPDSCmdShutdown
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class OPDSClient private constructor(
  private val parameters: OPDSClientParameters
) : OPDSClientType {

  private val logger =
    LoggerFactory.getLogger(OPDSClient::class.java)

  private val closed =
    AtomicBoolean(false)

  private val taskExecutor: ExecutorService =
    Executors.newSingleThreadExecutor { r ->
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
  private val entriesUngroupedSource: AttributeType<List<FeedEntry>> =
    OPDSClientAttributes.attributes.withValue(listOf())
  private val entriesGroupedSource: AttributeType<List<FeedGroup>> =
    OPDSClientAttributes.attributes.withValue(listOf())
  private val entrySource: AttributeType<FeedEntry> =
    OPDSClientAttributes.attributes.withValue(this.feedEntryCorrupt)

  private val stateUI: AttributeType<OPDSState> =
    OPDSClientAttributes.attributes.withValue(Initial)
  private val entriesUngroupedUI: AttributeType<List<FeedEntry>> =
    OPDSClientAttributes.attributes.withValue(listOf())
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
  private val entriesUngroupedUISub =
    this.resources.add(
      this.entriesUngroupedSource.subscribe { _, e ->
        this.parameters.runOnUI(Runnable { this.entriesUngroupedUI.set(e) })
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

  private val historyStack: ConcurrentLinkedDeque<OPDSStateHistoryParticipant> =
    ConcurrentLinkedDeque()

  private val mainTask =
    MainFeedTask(this)

  private class MainFeedTask(
    private val client: OPDSClient
  ) : Runnable, OPDSCmdContextType {

    private val commands =
      ArrayBlockingQueue<OPDSCmd>(10)

    override fun run() {
      while (this.shouldBeRunning()) {
        try {
          val c = this.commands.poll(100L, TimeUnit.MILLISECONDS) ?: continue
          if (c.taskFuture.isCancelled) {
            continue
          }
          c.execute(this)
        } catch (e: Throwable) {
          this.client.logger.warn("Exception in feed task: ", e)
        }
      }
    }

    fun cancel() {
      this.commands.forEach { task ->
        try {
          task.taskFuture.cancel(true)
        } catch (e: Throwable) {
          this.client.logger.warn("Cancellation failed: ", e)
        }
      }
    }

    private fun shouldBeRunning(): Boolean {
      return !this.client.closed.get()
    }

    fun enqueue(command: OPDSCmd): CompletableFuture<Unit> {
      try {
        this.commands.add(command)
        return command.taskFuture
      } catch (e: Throwable) {
        command.taskFuture.completeExceptionally(e)
        return command.taskFuture
      }
    }

    override fun setState(newState: OPDSState) {
      this.client.stateSet(newState)
    }

    override fun setStateReplaceTop(newState: OPDSState) {
      this.client.stateSource.set(newState)
    }

    override fun shutDown() {
      this.cancel()
      this.commands.clear()
    }

    override fun entriesUngrouped(): List<FeedEntry> {
      return this.client.entriesUngroupedSource.get()
    }

    override fun setEntriesUngrouped(entries: List<FeedEntry>) {
      this.client.entriesUngroupedSource.set(entries.toList())
    }

    override fun operationCancelled() {
      this.client.operationCancelled()
    }

    override val feedLoader: FeedLoaderType =
      this.client.parameters.feedLoader

    override val state: OPDSState
      get() = this.client.stateSource.get()
  }

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
      this.mainTask.enqueue(OPDSCmdShutdown())
    })
    this.resources.add(AutoCloseable {
      this.mainTask.cancel()
    })

    this.taskExecutor.execute(this.mainTask)
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
  override val entriesUngrouped: AttributeReadableType<List<FeedEntry>> =
    this.entriesUngroupedUI
  override val entriesGrouped: AttributeReadableType<List<FeedGroup>> =
    this.entriesGroupedUI
  override val entry: AttributeReadableType<FeedEntry> =
    this.entryUI

  override val hasHistory: Boolean
    get() = this.historyStack.isNotEmpty()

  override fun goBack(): CompletableFuture<Unit> {
    this.parameters.checkOnUI()

    val f = this.checkClosed()
    if (f != null) {
      return f
    }

    this.mainTask.cancel()

    if (this.historyStack.isEmpty()) {
      return CompletableFuture.completedFuture(Unit)
    }

    this.historyPop()
    return CompletableFuture.completedFuture(Unit)
  }

  private fun historyPop() {
    val oldState = this.historyStack.pop()
    this.historyTrace()
    this.updateAttributesForState(oldState)
    this.stateSource.set(oldState)
  }

  private fun historyTrace() {
    if (this.logger.isDebugEnabled) {
      this.logger.debug("History stack now: {}", this.historyStack.map { e -> e.javaClass.simpleName })
    }
  }

  override fun goTo(
    request: OPDSClientRequest
  ): CompletableFuture<Unit> {
    this.parameters.checkOnUI()

    val f = this.checkClosed()
    if (f != null) {
      return f
    }

    this.mainTask.cancel()
    return this.mainTask.enqueue(OPDSCmdExecuteRequest(request))
  }

  private fun checkClosed(): CompletableFuture<Unit>? {
    if (this.closed.get()) {
      val future = CompletableFuture<Unit>()
      future.completeExceptionally(IllegalStateException("Client is closed."))
      return future
    }
    return null
  }

  override fun loadMore(): CompletableFuture<Unit> {
    this.parameters.checkOnUI()

    val f = this.checkClosed()
    if (f != null) {
      return f
    }

    return this.mainTask.enqueue(OPDSCmdLoadMore())
  }

  override fun close() {
    if (this.closed.compareAndSet(false, true)) {
      this.resources.close()
    }
  }

  private fun stateSet(
    newState: OPDSState
  ) {
    val oldState = this.state.get()
    if (oldState is OPDSStateHistoryParticipant) {
      this.historyStack.push(oldState)
      this.historyTrace()
    }

    this.updateAttributesForState(newState)
    this.stateSource.set(newState)
  }

  private fun updateAttributesForState(
    newState: OPDSState
  ) {
    when (newState) {
      is OPDSState.LoadedFeedEntry -> {
        this.entrySource.set(newState.request.entry)
        this.entriesGroupedSource.set(listOf())
        this.entriesUngroupedSource.set(listOf())
      }

      is OPDSState.LoadedFeedWithGroups -> {
        this.entrySource.set(this.feedEntryCorrupt)
        this.entriesGroupedSource.set(newState.feed.feedGroupsInOrder.toList())
        this.entriesUngroupedSource.set(listOf())
      }

      is OPDSState.LoadedFeedWithoutGroups -> {
        this.entrySource.set(this.feedEntryCorrupt)
        this.entriesGroupedSource.set(listOf())
        this.entriesUngroupedSource.set(newState.feed.entriesInOrder.toList())
      }
      is OPDSState.Error -> {
        this.entrySource.set(this.feedEntryCorrupt)
        this.entriesGroupedSource.set(listOf())
        this.entriesUngroupedSource.set(listOf())
      }
      Initial -> {
        this.entrySource.set(this.feedEntryCorrupt)
        this.entriesGroupedSource.set(listOf())
        this.entriesUngroupedSource.set(listOf())
      }
      is OPDSState.Loading -> {
        this.entrySource.set(this.feedEntryCorrupt)
        this.entriesGroupedSource.set(listOf())
        this.entriesUngroupedSource.set(listOf())
      }
    }
  }

  private fun operationCancelled() {
    when (this.state.get()) {
      is OPDSState.Error -> {
        // Nothing to do.
      }

      Initial -> {
        // Nothing to do.
      }

      is OPDSState.Loading -> {
        if (this.historyStack.isEmpty()) {
          this.stateSource.set(Initial)
        } else {
          this.historyPop()
        }
      }

      is OPDSState.LoadedFeedEntry -> {
        // Nothing to do.
      }

      is OPDSState.LoadedFeedWithGroups -> {
        // Nothing to do.
      }

      is OPDSState.LoadedFeedWithoutGroups -> {
        // Nothing to do.
      }
    }
  }
}
