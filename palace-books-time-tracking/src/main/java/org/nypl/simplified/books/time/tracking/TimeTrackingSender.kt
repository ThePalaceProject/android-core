package org.nypl.simplified.books.time.tracking

import com.io7m.jmulticlose.core.CloseableCollection
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryDeleteType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntrySelectType
import org.thepalaceproject.palace.diagnostics.api.DiagnosticCodes
import org.thepalaceproject.palace.diagnostics.api.DiagnosticsServiceType
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The sender service.
 *
 * The sender service attempts to send all time tracking entries that have been recorded by
 * the merge service. It is responsible for grouping entries into single requests in order to
 * avoid overwhelming the remote side with lots of small requests, and is responsible for
 * deleting entries from the database when the remote side has accepted them.
 *
 * An entry is deleted from the database only after the remote side has acknowledged
 * receipt. Entries that fail to send are retained and retried on later ticks.
 *
 * If an entry refers to an account that no longer exists in the profile, it cannot be sent.
 * Such entries are retained in the database (they may be settled manually), a diagnostic is
 * reported once per account, and the entries are not retried.
 */

class TimeTrackingSender private constructor(
  private val database: DBType,
  private val profiles: ProfilesControllerType,
  private val httpCalls: TimeTrackingHTTPCallsType,
  private val audit: TimeTrackingAudit,
  private val frequency: Duration,
  private val diagnostics: DiagnosticsServiceType,
) : TimeTrackingSenderServiceType {
  private val logger =
    LoggerFactory.getLogger(TimeTrackingSender::class.java)

  private val tickWrite =
    LinkedBlockingQueue<Unit>(1)
  private val resources =
    CloseableCollection.create()

  private val orphanedAccountsReported =
    mutableSetOf<UUID>()

  private val executor =
    Executors.newSingleThreadScheduledExecutor { r ->
      val thread = Thread(r)
      thread.name = "org.nypl.simplified.books.time.tracking.sender[${thread.id}]"
      thread.isDaemon = true
      thread.priority = Thread.MIN_PRIORITY
      thread
    }

  init {
    this.resources.add(
      AutoCloseable {
        this.executor.shutdown()
        this.executor.awaitTermination(30L, TimeUnit.SECONDS)
      },
    )
    this.executor.scheduleWithFixedDelay(
      this::onTrySend,
      0L,
      this.frequency.toMillis(),
      TimeUnit.MILLISECONDS,
    )
    this.resources.add(
      AutoCloseable {
        this.tickWrite.offer(Unit)
      },
    )
  }

  private fun onTrySend() {
    try {
      val entries =
        this.database.openConnection().use { connection ->
          connection.openTransaction().use { transaction ->
            transaction.execute(DBQTimeTrackingEntrySelectType::class.java, Unit)
          }
        }

      val grouped = TimeTrackingEntryOutgoing.group(entries)
      for ((key, outgoingEntries) in grouped) {
        check(outgoingEntries.isNotEmpty()) {
          "Outgoing entries cannot be empty"
        }
        this.sendOneBatch(key, outgoingEntries)
      }
    } catch (e: Throwable) {
      this.logger.debug("Failed to send time tracking entries: ", e)
    } finally {
      this.tickWrite.offer(Unit)
    }
  }

  private fun sendOneBatch(
    key: TimeTrackingEntryOutgoing.Key,
    outgoingEntries: List<TimeTrackingEntryOutgoing>,
  ) {
    for (e in outgoingEntries) {
      this.audit.sendAttempt(e)
    }

    val account: AccountType
    try {
      account = this.profiles.profileCurrent().account(key.accountID)
    } catch (e: Throwable) {
      this.orphanedBatch(key, outgoingEntries, e)
      return
    }

    try {
      val response =
        this.httpCalls.registerTimeTrackingInfo(
          account = account,
          request =
            TimeTrackingRequest(
              bookId = key.bookID.value,
              libraryId = key.libraryID,
              timeTrackingUri = key.targetURI,
              timeEntries = outgoingEntries.map { e -> e.timeEntry },
            ),
        )

      /*
       * If the responses list is empty, and the count suggests that all entries succeeded, we
       * log success and delete the entries. Unfortunately, the spec leaves open the
       * possibility that there will be a non-zero number of failures, and nothing in the
       * responses list. In that edge case, we won't know _which_ entries failed and so we can
       * do nothing other than send every entry again. The server is responsible for rejecting
       * any entry that it has already received, so this should be safe.
       */

      if (response.responses.isEmpty()) {
        if (response.summary.successes == outgoingEntries.size && response.summary.failures == 0) {
          this.entriesSentSuccessfully(outgoingEntries)
          return
        }

        check(response.summary.failures != 0)
        outgoingEntries.forEach { e ->
          this.audit.sendAttemptFailed(e)
        }
        return
      }

      /*
       * For each successful response, we need to log the successful send attempt and then
       * delete the entry so that it isn't sent again. For each failed attempt, we simply log
       * the failure.
       */

      val succeededEntries = mutableListOf<TimeTrackingEntryOutgoing>()
      val permanentEntries = mutableListOf<TimeTrackingEntryOutgoing>()

      for (r in response.responses) {
        val e = this.findEntry(outgoingEntries, r.id)
        if (e == null) {
          continue
        }
        if (r.isStatusSuccess()) {
          succeededEntries.add(e)
        } else if (r.isStatusFailedPermanently()) {
          permanentEntries.add(e)
        } else {
          this.audit.sendAttemptFailed(e)
        }
      }

      for (e in succeededEntries) {
        this.audit.sendAttemptSucceeded(e)
      }
      for (e in permanentEntries) {
        this.audit.sendAttemptFailedPermanently(e)
      }

      val deletedIds =
        succeededEntries
          .map { e -> e.timeEntry.id }
          .plus(permanentEntries.map { e -> e.timeEntry.id })
      if (deletedIds.isNotEmpty()) {
        this.deleteEntries(deletedIds)
      }
    } catch (e: Throwable) {
      this.logger.debug("Failed to send time tracking entries: ", e)
      outgoingEntries.forEach { entry ->
        this.audit.sendAttemptFailedExceptionally(entry, e)
      }
    }
  }

  private fun findEntry(
    outgoingEntries: List<TimeTrackingEntryOutgoing>,
    entryId: String,
  ): TimeTrackingEntryOutgoing? {
    for (entry in outgoingEntries) {
      if (entry.timeEntry.id == entryId) {
        return entry
      }
    }
    return null
  }

  private fun orphanedBatch(
    key: TimeTrackingEntryOutgoing.Key,
    outgoingEntries: List<TimeTrackingEntryOutgoing>,
    exception: Throwable,
  ) {
    /*
     * The entries cannot be sent: the account no longer exists. Retain the entries in the
     * database so that they can be inspected and settled manually, and report the situation
     * once per account.
     */
    if (!this.orphanedAccountsReported.contains(key.accountID.uuid)) {
      this.orphanedAccountsReported.add(key.accountID.uuid)
      this.diagnostics.report(
        code = DiagnosticCodes.TIME_TRACKING_ORPHANED,
        data =
          mapOf(
            Pair("Account", key.accountID.toString()),
            Pair("Entries", outgoingEntries.size.toString()),
          ),
        throwable = exception,
      )
      this.logger.warn(
        "Time tracking entries for account {} cannot be sent: the account no longer exists",
        key.accountID,
      )
    }
  }

  private fun entriesSentSuccessfully(entries: List<TimeTrackingEntryOutgoing>) {
    for (e in entries) {
      this.audit.sendAttemptSucceeded(e)
    }
    val ids = entries.map { e -> e.timeEntry.id }
    if (ids.isNotEmpty()) {
      this.deleteEntries(ids)
    }
  }

  private fun deleteEntries(ids: List<String>) {
    this.database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingEntryDeleteType::class.java, ids)
        transaction.commit()
      }
    }
  }

  companion object {
    /**
     * Create a new sender service.
     *
     * @param database The database
     * @param profiles The profiles controller
     * @param httpCalls The HTTP calls object
     * @param audit The event log
     * @param frequency The frequency of ticks
     * @param diagnostics The diagnostics service
     *
     * @return The sender service
     */
    fun create(
      database: DBType,
      profiles: ProfilesControllerType,
      httpCalls: TimeTrackingHTTPCallsType,
      audit: TimeTrackingAudit,
      frequency: Duration,
      diagnostics: DiagnosticsServiceType,
    ): TimeTrackingSenderServiceType =
      TimeTrackingSender(
        database = database,
        profiles = profiles,
        httpCalls = httpCalls,
        audit = audit,
        frequency = frequency,
        diagnostics = diagnostics,
      )
  }

  override fun awaitWrite(
    timeout: Long,
    unit: TimeUnit,
  ) {
    this.tickWrite.poll(timeout, unit)
  }

  override fun close() {
    this.resources.close()
  }
}
