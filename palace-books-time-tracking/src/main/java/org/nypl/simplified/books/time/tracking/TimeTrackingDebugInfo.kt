package org.nypl.simplified.books.time.tracking

import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryCreated
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEntryOutgoing
import org.nypl.simplified.books.time.tracking.api.TimeTrackingEvent
import org.nypl.simplified.books.time.tracking.api.TimeTrackingReceivedSpan
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttempt
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailed
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailedExceptionally
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptFailedPermanently
import org.nypl.simplified.books.time.tracking.api.TimeTrackingSendAttemptSucceeded
import org.nypl.simplified.books.time.tracking.api.TimeTrackingTime
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntrySelectType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventSelectType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanSelectType
import java.time.Duration
import java.time.OffsetDateTime

/**
 * The construction of a plain text summary of the state of the time tracking pipeline,
 * for display in the debug menu.
 *
 * The summary is a snapshot: it reflects the state of the database and the service at the
 * time at which the summary was requested, and is not updated automatically.
 */
object TimeTrackingDebugInfo {
  /*
   * The merge service selects spans which were received at least 90 seconds ago, once every
   * 30 seconds. A span which is older than two minutes has therefore had at least one
   * opportunity to be merged, and a non-zero count of such spans indicates that the merge
   * service is not making progress.
   */
  private val SPAN_STALE_THRESHOLD =
    Duration.ofMinutes(2L)

  /*
   * The sender service records a send attempt event before it makes the HTTP call, on every
   * tick in which there are entries to send. Entries which are pending while there have been
   * no send attempt events for ten minutes indicate that the sender service is not making
   * progress.
   */
  private val SEND_ATTEMPT_WINDOW =
    Duration.ofMinutes(10L)

  /*
   * The window of events displayed in the summary.
   */
  private val EVENT_WINDOW =
    Duration.ofHours(24L)

  /*
   * The maximum number of events displayed in the summary.
   */
  private val RECENT_EVENT_COUNT =
    20

  /**
   * Build the lines of the summary.
   *
   * @param database The database
   * @param status The current time tracking status
   * @param now The current time
   *
   * @return The lines
   */
  fun lines(
    database: DBType,
    status: TimeTrackingStatus,
    now: OffsetDateTime,
  ): List<String> {
    val spans =
      selectSpans(database, now)
    val entries =
      selectEntries(database)
    val eventsInWindow =
      selectEventsSince(database, now.minus(EVENT_WINDOW))

    val lines =
      mutableListOf<String>()

    lines.add("Session")
    when (val s = status) {
      is TimeTrackingStatus.Active -> {
        lines.add("  Active")
        lines.add("  account: " + s.accountID.toString())
        lines.add("  book: " + s.bookId.value)
        lines.add("  library: " + s.libraryId)
        lines.add("  uri: " + s.timeTrackingUri.toString())
      }

      TimeTrackingStatus.Inactive -> {
        lines.add("  Inactive")
      }
    }
    lines.add("")

    lines.add("Spans (received, not yet merged)")
    lines.add("  count: " + spans.size.toString())
    if (spans.isNotEmpty()) {
      val oldestReceived =
        oldestSpanReceiveTime(spans)
      lines.add("  oldest received: " + formatAge(oldestReceived, now) + " ago")
      val staleCount =
        spans.count { span ->
          Duration.between(span.timeReceived, now) > SPAN_STALE_THRESHOLD
        }
      lines.add("  older than 2 minutes: " + staleCount.toString() + " (expected 0)")
    }
    lines.add("")

    lines.add("Entries (merged, not yet sent)")
    lines.add("  count: " + entries.size.toString())
    if (entries.isNotEmpty()) {
      val totalSeconds =
        entries.sumOf { it.timeEntry.secondsPlayed.toLong() }
      lines.add(
        "  total: " + totalSeconds.toString() + " seconds (" + formatMinutes(totalSeconds) + ")",
      )
      val oldestDuringMinute =
        oldestEntryDuringMinute(entries)
      lines.add("  oldest during-minute: " + formatAge(oldestDuringMinute, now) + " ago")
      lines.add("  by account:")
      val byAccount =
        entries.groupBy { entry -> entry.accountID.toString() }
      for ((accountIDText, accountEntries) in byAccount) {
        val accountSeconds =
          accountEntries.sumOf { it.timeEntry.secondsPlayed.toLong() }
        lines.add(
          "    " +
            accountIDText +
            ": " +
            accountEntries.size.toString() +
            " entries, " +
            accountSeconds.toString() +
            " seconds",
        )
      }
    }
    lines.add("")

    val attemptCount =
      eventsInWindow.count { event ->
        isSendAttemptOperation(event.operationName)
      }
    lines.add("Health")
    if (entries.isNotEmpty() && attemptCount == 0) {
      lines.add("  WARNING: entries are pending, but there have been no send attempts in the last 10 minutes")
    } else {
      lines.add("  OK")
    }
    lines.add("")

    val shownCount =
      minOf(RECENT_EVENT_COUNT, eventsInWindow.size)
    lines.add(
      "Events (last 24 hours: " +
        eventsInWindow.size.toString() +
        " total, showing most recent " +
        shownCount.toString() +
        ")",
    )
    for (event in eventsInWindow.takeLast(shownCount)) {
      lines.add("  " + formatEventLine(event))
    }

    return lines
  }

  private fun selectSpans(
    database: DBType,
    cutoff: OffsetDateTime,
  ): List<TimeTrackingReceivedSpan> {
    return database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingSpanSelectType::class.java, cutoff)
      }
    }
  }

  private fun selectEntries(database: DBType): List<TimeTrackingEntryOutgoing> {
    return database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingEntrySelectType::class.java, Unit)
      }
    }
  }

  private fun selectEventsSince(
    database: DBType,
    since: OffsetDateTime,
  ): List<TimeTrackingEvent> {
    return database.openConnection().use { connection ->
      connection.openTransaction().use { transaction ->
        transaction.execute(DBQTimeTrackingEventSelectType::class.java, since)
      }
    }
  }

  private fun oldestSpanReceiveTime(spans: List<TimeTrackingReceivedSpan>): OffsetDateTime {
    var oldest =
      spans[0].timeReceived
    for (span in spans) {
      if (span.timeReceived.isBefore(oldest)) {
        oldest = span.timeReceived
      }
    }
    return oldest
  }

  private fun oldestEntryDuringMinute(entries: List<TimeTrackingEntryOutgoing>): OffsetDateTime {
    var oldest =
      entries[0].timeEntry.duringMinute
    for (entry in entries) {
      if (entry.timeEntry.duringMinute.isBefore(oldest)) {
        oldest = entry.timeEntry.duringMinute
      }
    }
    return oldest
  }

  private fun isSendAttemptOperation(operationName: String): Boolean {
    return operationName == TimeTrackingSendAttempt.OPERATION_NAME ||
      operationName == TimeTrackingSendAttemptSucceeded.OPERATION_NAME ||
      operationName == TimeTrackingSendAttemptFailed.OPERATION_NAME ||
      operationName == TimeTrackingSendAttemptFailedExceptionally.OPERATION_NAME ||
      operationName == TimeTrackingSendAttemptFailedPermanently.OPERATION_NAME
  }

  private fun formatEventLine(event: TimeTrackingEvent): String {
    val parts =
      mutableListOf(
        TimeTrackingTime.format(event.time),
        event.operationName,
        "book=" + event.bookID,
      )
    val entryID: String
    val secondsPlayed: Int?

    when (val e = event) {
      is TimeTrackingEntryCreated -> {
        entryID = e.entryID
        secondsPlayed = e.secondsPlayed
      }

      is TimeTrackingSendAttempt -> {
        entryID = e.entryID
        secondsPlayed = e.secondsPlayed
      }

      is TimeTrackingSendAttemptSucceeded -> {
        entryID = e.entryID
        secondsPlayed = e.secondsPlayed
      }

      is TimeTrackingSendAttemptFailed -> {
        entryID = e.entryID
        secondsPlayed = e.secondsPlayed
      }

      is TimeTrackingSendAttemptFailedExceptionally -> {
        entryID = e.entryID
        secondsPlayed = e.secondsPlayed
      }

      is TimeTrackingSendAttemptFailedPermanently -> {
        entryID = e.entryID
        secondsPlayed = e.secondsPlayed
      }

      else -> {
        entryID = ""
        secondsPlayed = null
      }
    }

    if (entryID.isNotEmpty()) {
      parts.add("entry=" + entryID)
    }
    if (secondsPlayed != null) {
      parts.add("seconds=" + secondsPlayed.toString())
    }
    if (event is TimeTrackingSendAttemptFailedExceptionally) {
      parts.add("exception=" + event.exception)
    }

    return parts.joinToString("  ")
  }

  private fun formatAge(
    then: OffsetDateTime,
    now: OffsetDateTime,
  ): String {
    var seconds =
      Duration.between(then, now).seconds
    if (seconds < 0L) {
      seconds = 0L
    }
    val days =
      seconds / 86_400L
    val hours =
      (seconds % 86_400L) / 3_600L
    val minutes =
      (seconds % 3_600L) / 60L
    val secs =
      seconds % 60L

    val parts =
      mutableListOf<String>()
    if (days > 0L) {
      parts.add(days.toString() + "d")
    }
    if (hours > 0L || days > 0L) {
      parts.add(hours.toString() + "h")
    }
    if (minutes > 0L || hours > 0L || days > 0L) {
      parts.add(minutes.toString() + "m")
    }
    parts.add(secs.toString() + "s")
    return parts.joinToString(" ")
  }

  private fun formatMinutes(seconds: Long): String {
    val whole =
      seconds / 60L
    val remainder =
      seconds % 60L
    if (remainder == 0L) {
      return whole.toString() + " min"
    }
    val tenths =
      (remainder * 10L) / 60L
    return whole.toString() + "." + tenths.toString() + " min"
  }
}
