package org.nypl.simplified.books.time.tracking.api

import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import java.net.URI
import java.time.OffsetDateTime
import java.util.Properties
import java.util.UUID

/**
 * A span of time tracked by the player.
 *
 * A span is the raw unit of time tracking; a contiguous interval of playback of a single book
 * by a single account. Spans are merged into [TimeTrackingEntry] values before being sent to
 * the server.
 *
 * @param id The unique identifier of the span, as reported by the player
 * @param accountID The account under which the book is being read
 * @param libraryID The identifier of the account provider
 * @param bookID The identifier of the book
 * @param timeStarted The time at which the span began
 * @param timeEnded The time at which the span ended
 * @param timeReceived The time at which the span was recorded locally
 * @param targetURI The URI to which time tracking information for the book is sent
 */
data class TimeTrackingReceivedSpan(
  val id: UUID,
  val accountID: AccountID,
  val libraryID: URI,
  val bookID: PlayerPalaceID,
  val timeStarted: OffsetDateTime,
  val timeEnded: OffsetDateTime,
  val timeReceived: OffsetDateTime,
  val targetURI: URI,
) {
  companion object {
    /**
     * Parse a span from the legacy file-based format.
     *
     * The legacy format did not record a receive time; the caller must supply one (the
     * convention was to use the file modification time).
     *
     * @param properties The parsed properties of a legacy span file
     * @param timeReceived The time at which the span is considered to have been received
     *
     * @return The span
     */
    fun ofProperties(
      properties: Properties,
      timeReceived: OffsetDateTime,
    ): TimeTrackingReceivedSpan =
      TimeTrackingReceivedSpan(
        id = UUID.fromString(properties.getProperty("ID")),
        accountID = AccountID(UUID.fromString(properties.getProperty("AccountID"))),
        libraryID = URI.create(properties.getProperty("LibraryID")),
        bookID = PlayerPalaceID(properties.getProperty("BookID")),
        timeStarted = OffsetDateTime.parse(properties.getProperty("TimeStarted")),
        timeEnded = OffsetDateTime.parse(properties.getProperty("TimeEnded")),
        timeReceived = timeReceived,
        targetURI = URI.create(properties.getProperty("TargetURI")),
      )
  }
}
