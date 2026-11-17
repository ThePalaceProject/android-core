package org.nypl.simplified.books.borrowing

import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.feeds.api.FeedAcquisitionPath
import org.nypl.simplified.feeds.api.FeedAcquisitionPaths
import org.nypl.simplified.feeds.api.FeedAcquisitionRelation
import org.nypl.simplified.feeds.api.FeedEntryBook

/**
 * Functions to select book acquisitions based on the available format support.
 */

object BorrowAcquisitions {
  /**
   * Pick the preferred acquisition for the feed entry.
   */

  fun pickBestAcquisitionPath(
    support: BookFormatSupportType,
    entry: FeedEntryBook,
  ): FeedAcquisitionPath? {
    val paths = FeedAcquisitionPaths.linearize(entry.acquisitions)
    return paths
      .filter { support.isSupportedPath(it.asMIMETypes()) }
      // when borrowing a book, we need to ignore if the acquisition is for a sample/preview
      .firstOrNull { it.source.relation != FeedAcquisitionRelation.ACQUISITION_SAMPLE }
  }
}
