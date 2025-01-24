package org.thepalaceproject.opds.client

import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
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
    this.stateSource.subscribe { _, newValue ->
      this.parameters.runOnUI(Runnable { this.stateUI.set(newValue) })
    }
  private val entriesUngroupedUISub =
    this.entriesUngroupedSource.subscribe { _, e ->
      this.parameters.runOnUI(Runnable { this.entriesUngroupedUI.set(e) })
    }
  private val entriesGroupedUISub =
    this.entriesGroupedSource.subscribe { _, e ->
      this.parameters.runOnUI(Runnable { this.entriesGroupedUI.set(e) })
    }
  private val entryUISub =
    this.entrySource.subscribe { _, newValue ->
      this.parameters.runOnUI(Runnable { this.entryUI.set(newValue) })
    }

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

    override fun setEntriesUngrouped(entries: List<FeedEntry>) {
      this.client.entriesUngroupedSource.set(entries)
    }

    override fun setEntriesGrouped(groups: List<FeedGroup>) {
      this.client.entriesGroupedSource.set(groups)
    }

    override fun setState(newState: OPDSState) {
      this.client.stateSource.set(newState)
    }

    override fun setStateSavingHistory(newState: OPDSStateHistoryParticipant) {
      this.client.stateSetSavingHistory(newState)
    }

    override fun shutDown() {
      this.cancel()
      this.commands.clear()
    }

    override fun entriesUngrouped(): List<FeedEntry> {
      return this.client.entriesUngroupedSource.get()
    }

    override val feedLoader: FeedLoaderType =
      this.client.parameters.feedLoader

    override val state: OPDSState
      get() = this.client.stateSource.get()
  }

  init {
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
    this.mainTask.cancel()

    val future = CompletableFuture<Unit>()
    future.completeExceptionally(IllegalStateException("Unimplemented code!"))
    return future
  }

  override fun goTo(
    request: OPDSClientRequest
  ): CompletableFuture<Unit> {
    this.parameters.checkOnUI()
    this.mainTask.cancel()
    return this.mainTask.enqueue(OPDSCmdExecuteRequest(request))
  }

  override fun loadMore(): CompletableFuture<Unit> {
    this.parameters.checkOnUI()
    return this.mainTask.enqueue(OPDSCmdLoadMore())
  }

  override fun close() {
    if (this.closed.compareAndSet(false, true)) {
      this.mainTask.cancel()
      this.mainTask.enqueue(OPDSCmdShutdown())
      this.taskExecutor.shutdown()
      this.taskExecutor.awaitTermination(5L, TimeUnit.SECONDS)
    }
  }

  private fun stateSetSavingHistory(
    newState: OPDSStateHistoryParticipant
  ) {
    val oldState = this.state.get()
    if (oldState is OPDSStateHistoryParticipant) {
      this.historyStack.push(oldState)
    }
    this.stateSource.set(newState)
  }
}
