package org.nypl.simplified.books.time.tracking.api

import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import java.net.URI
import java.time.OffsetDateTime
import java.util.Properties
import java.util.UUID

/**
 * A time tracking entry along with the addressing information required to send it.
 *
 * @param accountID The account under which the book is being read
 * @param libraryID The identifier of the account provider
 * @param bookID The identifier of the book
 * @param targetURI The URI to which time tracking information for the book is sent
 * @param timeEntry The entry itself
 */
data class TimeTrackingEntryOutgoing(
  val accountID: AccountID,
  val libraryID: URI,
  val bookID: PlayerPalaceID,
  val targetURI: URI,
  val timeEntry: TimeTrackingEntry,
) {
  /**
   * The grouping key used when combining entries into single send requests.
   */
  data class Key(
    val accountID: AccountID,
    val bookID: PlayerPalaceID,
    val libraryID: URI,
    val targetURI: URI,
  )

  companion object {
    /**
     * Group a list of entries by the key under which they will be sent.
     *
     * @param entries The entries
     *
     * @return The entries grouped by send key
     */
    fun group(entries: List<TimeTrackingEntryOutgoing>): Map<Key, List<TimeTrackingEntryOutgoing>> {
      val results = mutableMapOf<Key, List<TimeTrackingEntryOutgoing>>()
      for (entry in entries) {
        val key =
          Key(
            accountID = entry.accountID,
            bookID = entry.bookID,
            libraryID = entry.libraryID,
            targetURI = entry.targetURI,
          )
        var existing = results[key]
        existing = existing?.plus(entry) ?: listOf(entry)
        results[key] = existing
      }
      return results.toMap()
    }

    /**
     * Parse an entry from the legacy file-based format.
     *
     * @param properties The parsed properties of a legacy entry file
     *
     * @return The entry
     */
    fun ofProperties(properties: Properties): TimeTrackingEntryOutgoing =
      TimeTrackingEntryOutgoing(
        accountID = AccountID(UUID.fromString(properties.getProperty("AccountID"))),
        bookID = PlayerPalaceID(properties.getProperty("BookID")),
        targetURI = URI.create(properties.getProperty("TargetURI")),
        libraryID = URI.create(properties.getProperty("LibraryID")),
        timeEntry =
          TimeTrackingEntry(
            id = properties.getProperty("ID"),
            duringMinute = OffsetDateTime.parse(properties.getProperty("Minute")),
            secondsPlayed = properties.getProperty("Seconds").toInt(),
          ),
      )
  }
}
