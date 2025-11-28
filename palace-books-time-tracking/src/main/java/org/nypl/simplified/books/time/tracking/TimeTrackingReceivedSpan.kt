package org.nypl.simplified.books.time.tracking

import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountID
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Properties
import java.util.UUID

data class TimeTrackingReceivedSpan(
  val id: UUID,
  val accountID: AccountID,
  val libraryID: URI,
  val bookID: PlayerPalaceID,
  val timeStarted: OffsetDateTime,
  val timeEnded: OffsetDateTime,
  val targetURI: URI
) {

  init {
    require(this.timeStarted.offset == ZoneOffset.UTC) {
      "Times must be in UTC"
    }
    require(this.timeEnded.offset == ZoneOffset.UTC) {
      "Times must be in UTC"
    }
  }

  fun toBytes(): ByteArray {
    return ByteArrayOutputStream().use { s ->
      val p = this.toProperties()
      p.store(s, "")
      s.toByteArray()
    }
  }

  fun toProperties(): Properties {
    val p = Properties()
    p.setProperty("@Type", "TimeTrackingReceivedSpan")
    p.setProperty("@Version", "1")
    p.setProperty("ID", this.id.toString())
    p.setProperty("AccountID", this.accountID.uuid.toString())
    p.setProperty("BookID", this.bookID.value)
    p.setProperty("LibraryID", this.libraryID.toString())
    p.setProperty("TargetURI", this.targetURI.toString())
    p.setProperty("TimeEnded", this.timeEnded.toString())
    p.setProperty("TimeStarted", this.timeStarted.toString())
    return p
  }

  companion object {
    fun ofFile(
      file: Path
    ): TimeTrackingReceivedSpan {
      return Files.newInputStream(file).use { stream ->
        val p = Properties()
        p.load(stream)
        ofProperties(p)
      }
    }

    fun ofProperties(
      p: Properties
    ): TimeTrackingReceivedSpan {
      return TimeTrackingReceivedSpan(
        id = UUID.fromString(p.getProperty("ID")),
        accountID = AccountID(UUID.fromString(p.getProperty("AccountID"))),
        libraryID = URI.create(p.getProperty("LibraryID")),
        bookID = PlayerPalaceID(p.getProperty("BookID")),
        timeStarted = OffsetDateTime.parse(p.getProperty("TimeStarted")),
        timeEnded = OffsetDateTime.parse(p.getProperty("TimeEnded")),
        targetURI = URI.create(p.getProperty("TargetURI"))
      )
    }
  }
}
