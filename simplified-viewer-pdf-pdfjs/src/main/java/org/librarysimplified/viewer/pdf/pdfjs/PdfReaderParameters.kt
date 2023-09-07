package org.librarysimplified.viewer.pdf.pdfjs

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.feeds.api.FeedEntry
import java.io.File
import java.io.Serializable

/**
 * Represents the parameters to pass to the [PdfReaderActivity].
 *
 * @property accountId Account holders ID
 * @property documentTitle String title of the PDF PDF Book
 * @property pdfFile PDF file to load
 * @property id The BookID for the PDF Book
 */
data class PdfReaderParameters(
  val accountId: AccountID,
  val documentTitle: String,
  val pdfFile: File,
  val id: BookID,
  val entry: FeedEntry.FeedEntryOPDS,
  val drmInfo: BookDRMInformation
) : Serializable
