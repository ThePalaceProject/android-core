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
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime

class TimeTrackingService private constructor(
  private val status: AttributeType<TimeTrackingStatus>,
  private val collector: TimeTrackingCollectorServiceType,
  private val merge: TimeTrackingMergeServiceType,
  private val sender: TimeTrackingSenderServiceType
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

    fun create(
      profiles: ProfilesControllerType,
      httpCalls: TimeTrackingHTTPCallsType,
      clock: () -> OffsetDateTime,
      timeSegments: Observable<PlayerTimeTracked>,
      debugDirectory: Path,
      collectorDirectory: Path,
      senderDirectory: Path,
    ): TimeTrackingServiceType {
      val status: AttributeType<TimeTrackingStatus> =
        Attributes.create { e -> this.logger.debug("Attribute exception: ", e) }
          .withValue(TimeTrackingStatus.Inactive)

      return TimeTrackingService(
        status = status,
        collector = TimeTrackingCollector.create(
          profiles = profiles,
          status = status,
          timeSegments = timeSegments,
          debugDirectory = debugDirectory,
          outputDirectory = collectorDirectory
        ),
        merge = TimeTrackingMerge.create(
          clock = clock,
          frequency = Duration.ofSeconds(30L),
          inputDirectory = collectorDirectory,
          debugDirectory = debugDirectory,
          outputDirectory = senderDirectory
        ),
        sender = TimeTrackingSender.create(
          profiles = profiles,
          httpCalls = httpCalls,
          debugDirectory = debugDirectory,
          inputDirectory = senderDirectory,
          frequency = Duration.ofSeconds(30L)
        )
      )
    }
  }

  override fun onBookOpenedForTracking(
    accountID: AccountID,
    bookId: PlayerPalaceID,
    libraryId: String,
    timeTrackingUri: URI
  ) {
    this.status.set(TimeTrackingStatus.Active(
      accountID = accountID,
      bookId = bookId,
      libraryId = libraryId,
      timeTrackingUri = timeTrackingUri
    ))
  }

  override fun onBookClosed() {
    this.status.set(TimeTrackingStatus.Inactive)
  }

  override fun close() {
    this.resources.close()
  }
}
