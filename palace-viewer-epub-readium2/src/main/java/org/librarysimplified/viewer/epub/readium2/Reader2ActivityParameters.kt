package org.librarysimplified.viewer.epub.readium2

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.feeds.api.FeedEntryBook
import java.io.File

data class Reader2ActivityParameters(
  val accountId: AccountID,
  val bookId: BookID,
  val file: File,
  val drmInfo: BookDRMInformation,
  val entry: FeedEntryBook,
)
