package org.nypl.simplified.ui.catalog

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.borrowing.SAMLDownloadContext
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry

data class CatalogBorrowParameters(
  val accountID: AccountID,
  val bookID: BookID,
  val entry: OPDSAcquisitionFeedEntry,
  val samlDownloadContext: SAMLDownloadContext?
)
