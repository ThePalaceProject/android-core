package org.nypl.simplified.books.time.tracking

import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.UUID

data class TimeTrackingEntryOutgoing(
  val accountID: AccountID,
  val libraryID: URI,
  val bookID: PlayerPalaceID,
  val targetURI: URI,
  val timeEntry: TimeTrackingEntry,
) {
  fun toProperties(): Properties {
    val p = Properties()
    p.setProperty("@Type", "TimeTrackingEntryOutgoing")
    p.setProperty("@Version", "1")
    p.setProperty("ID", this.timeEntry.id)

    p.setProperty("AccountID", this.accountID.uuid.toString())
    p.setProperty("BookID", this.bookID.value)
    p.setProperty("LibraryID", this.libraryID.toString())
    p.setProperty("Minute", this.timeEntry.duringMinute)
    p.setProperty("Seconds", this.timeEntry.secondsPlayed.toString())
    p.setProperty("TargetURI", this.targetURI.toString())
    return p
  }

  data class Key(
    val accountID: AccountID,
    val bookID: PlayerPalaceID,
    val libraryID: URI,
    val targetURI: URI,
  )

  companion object {

    fun group(
      entries: List<TimeTrackingEntryOutgoing>
    ): Map<Key, List<TimeTrackingEntryOutgoing>> {
      val results = mutableMapOf<Key, List<TimeTrackingEntryOutgoing>>()
      for (entry in entries) {
        val key = Key(
          accountID = entry.accountID,
          bookID = entry.bookID,
          libraryID = entry.libraryID,
          targetURI = entry.targetURI
        )
        var existing = results[key]
        existing = existing?.plus(entry) ?: listOf()
        results[key] = existing
      }
      return results.toMap()
    }

    fun ofFile(
      file: Path
    ): TimeTrackingEntryOutgoing {
      return Files.newInputStream(file).use { stream ->
        val p = Properties()
        p.load(stream)
        ofProperties(p)
      }
    }

    fun ofProperties(
      p: Properties
    ): TimeTrackingEntryOutgoing {
      return TimeTrackingEntryOutgoing(
        accountID = AccountID(UUID.fromString(p.getProperty("AccountID"))),
        bookID = PlayerPalaceID(p.getProperty("BookID")),
        targetURI = URI.create(p.getProperty("TargetURI")),
        libraryID = URI.create(p.getProperty("LibraryID")),
        timeEntry = TimeTrackingEntry(
          id = p.getProperty("ID"),
          duringMinute = p.getProperty("Minute"),
          secondsPlayed = p.getProperty("Seconds").toInt()
        )
      )
    }
  }
}
