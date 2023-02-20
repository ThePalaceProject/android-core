package org.nypl.simplified.ui.catalog

import androidx.lifecycle.ViewModel
import org.nypl.simplified.books.controller.api.BooksPreviewControllerType
import org.nypl.simplified.feeds.api.FeedEntry
import org.slf4j.LoggerFactory

class CatalogBookPreviewViewModel(
  private val booksPreviewController: BooksPreviewControllerType
) : ViewModel() {

  private val logger =
    LoggerFactory.getLogger(CatalogBookPreviewViewModel::class.java)

  fun handlePreviewStatus(entry: FeedEntry.FeedEntryOPDS) {
    this.logger.debug("handling preview status: {}", entry.bookID)

    booksPreviewController.handleBookPreviewStatus(entry)
  }
}
