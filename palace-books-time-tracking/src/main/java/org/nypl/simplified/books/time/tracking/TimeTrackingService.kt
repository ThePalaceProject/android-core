package org.nypl.simplified.books.time.tracking

import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import com.io7m.jmulticlose.core.CloseableCollection
import io.reactivex.Observable
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.time_tracking.PlayerTimeTracked
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.palace.diagnostics.api.DiagnosticsServiceType
import java.net.URI
import java.time.Duration
import java.time.OffsetDateTime

/**
 * The time tracking service.
 *
 * The service consists of three sub-services:
 *
 * - A collector, which listens for a stream of "time tracked" events and records them as
 *   spans in the database.
 * - A merge service, which periodically merges old spans into time tracking entries.
 * - A sender service, which periodically sends time tracking entries to the server and
 *   deletes them from the database when the server has acknowledged receipt.
 */
class TimeTrackingService private constructor(
  private val status: AttributeType<TimeTrackingStatus>,
  private val collector: TimeTrackingCollectorServiceType,
  private val merge: TimeTrackingMergeServiceType,
  private val sender: TimeTrackingSenderServiceType,
) : TimeTrackingServiceType {
  private val resources =
    CloseableCollection.create()

  init {
    this.resources.add(this.collector)
    this.resources.add(this.merge)
    this.resources.add(this.sender)
  }

  companion object {
    private val logger =
      LoggerFactory.getLogger(TimeTrackingService::class.java)

    /**
     * Create a new time tracking service.
     *
     * @param database The database
     * @param profiles The profiles controller
     * @param httpCalls The HTTP calls object
     * @param clock The clock
     * @param timeSegments The observable stream of time tracked segments
     * @param diagnostics The diagnostics service
     *
     * @return The service
     */
    fun create(
      database: DBType,
      profiles: ProfilesControllerType,
      httpCalls: TimeTrackingHTTPCallsType,
      clock: () -> OffsetDateTime,
      timeSegments: Observable<PlayerTimeTracked>,
      diagnostics: DiagnosticsServiceType,
    ): TimeTrackingServiceType {
      val status: AttributeType<TimeTrackingStatus> =
        Attributes
          .create { e -> this.logger.debug("Attribute exception: ", e) }
          .withValue(TimeTrackingStatus.Inactive)

      val audit =
        TimeTrackingAudit.create(
          database = database,
          clock = clock,
          diagnostics = diagnostics,
        )

      return TimeTrackingService(
        status = status,
        collector =
          TimeTrackingCollector.create(
            database = database,
            profiles = profiles,
            status = status,
            timeSegments = timeSegments,
            audit = audit,
            clock = clock,
            diagnostics = diagnostics,
          ),
        merge =
          TimeTrackingMerge.create(
            database = database,
            clock = clock,
            frequency = Duration.ofSeconds(30L),
            diagnostics = diagnostics,
          ),
        sender =
          TimeTrackingSender.create(
            database = database,
            profiles = profiles,
            httpCalls = httpCalls,
            audit = audit,
            frequency = Duration.ofSeconds(30L),
            diagnostics = diagnostics,
          ),
      )
    }
  }

  override fun status(): TimeTrackingStatus {
    return this.status.get()
  }

  override fun onBookOpenedForTracking(
    accountID: AccountID,
    bookId: PlayerPalaceID,
    libraryId: String,
    timeTrackingUri: URI,
  ) {
    this.status.set(
      TimeTrackingStatus.Active(
        accountID = accountID,
        bookId = bookId,
        libraryId = libraryId,
        timeTrackingUri = timeTrackingUri,
      ),
    )
  }

  override fun onBookClosed() {
    this.status.set(TimeTrackingStatus.Inactive)
  }

  override fun close() {
    this.resources.close()
  }
}
