package org.nypl.simplified.books.preview

import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedPreviewAcquisition

object BookPreviewAcquisitions {
  /**
   * Pick the preferred preview acquisition for the feed entry. We use the first supported
   * acquisition, effectively allowing the server to set preferences.
   */

  fun pickBestPreviewAcquisition(entry: FeedEntryBook): FeedPreviewAcquisition? {
    return entry.previewAcquisitions.firstOrNull {
      StandardFormatNames.bookPreviewFiles.contains(it.type)
    }
  }
}
