package org.nypl.simplified.tests.mocking

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryType
import org.nypl.simplified.books.book_database.api.BookDatabaseType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.slf4j.LoggerFactory
import java.io.File
import java.util.SortedSet

class MockBookDatabase(
  val owner: AccountID,
  val booksDirectory: File,
) : BookDatabaseType {

  private val logger =
    LoggerFactory.getLogger(MockBookDatabase::class.java)

  var deleted = false
  val entries = mutableMapOf<BookID, MockBookDatabaseEntry>()

  override fun owner(): AccountID {
    check(!this.deleted) { "Database must not be deleted." }
    return this.owner
  }

  override fun books(): SortedSet<BookID> {
    check(!this.deleted) { "Database must not be deleted." }
    return this.entries.keys.toSortedSet()
  }

  override fun delete() {
    this.deleted = true
  }

  override fun createOrUpdate(
    id: BookID,
    entry: OPDSAcquisitionFeedEntry
  ): MockBookDatabaseEntry {
    this.logger.debug("createOrUpdate: [{}]", id)
    check(!this.deleted) { "Database must not be deleted." }

    val existing = this.entries[id]
    if (existing != null) {
      this.logger.debug("createOrUpdate: [{}]: rewriting existing", id)
      existing.writeOPDSEntry(entry)
      return existing
    }

    this.logger.debug("createOrUpdate: [{}]: creating new entry", id)

    val newEntry =
      MockBookDatabaseEntry(
        booksDirectory = this.booksDirectory,
        bookInitial = Book(
          account = this.owner,
          cover = null,
          entry = entry,
          formats = listOf(),
          id = id,
          thumbnail = null,
        )
      )

    newEntry.writeOPDSEntry(entry)
    this.entries[id] = newEntry
    return newEntry
  }

  override fun entry(
    id: BookID
  ): MockBookDatabaseEntry {
    check(!this.deleted) { "Database must not be deleted." }
    return this.entries[id] ?: throw IllegalStateException("No such database entry")
  }
}
