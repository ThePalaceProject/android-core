package org.nypl.simplified.ui.catalog

import android.content.res.Resources
import org.librarysimplified.ui.R
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.opds12.toOPDSAcquisitionFeedEntry

/**
 * Strings related to accessibility.
 */

object CatalogBookAccessibilityStrings {
  /**
   * The content description for cover images.
   */

  fun coverDescription(
    resources: Resources,
    feedEntry: FeedEntryBook
  ): String =
    when (BookFormats.inferFormat(feedEntry.toOPDSAcquisitionFeedEntry())) {
      BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB -> {
        resources.getString(
          R.string.catalogAccessibilityCoverEpub,
          feedEntry.title,
          feedEntry.authorsCommaSeparated
        )
      }

      BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO -> {
        resources.getString(
          R.string.catalogAccessibilityCoverAudiobook,
          feedEntry.title,
          feedEntry.authorsCommaSeparated
        )
      }

      BookFormats.BookFormatDefinition.BOOK_FORMAT_PDF -> {
        resources.getString(
          R.string.catalogAccessibilityCoverPdf,
          feedEntry.title,
          feedEntry.authorsCommaSeparated
        )
      }

      null -> {
        resources.getString(
          R.string.catalogAccessibilityCoverUnknownFormat,
          feedEntry.title,
          feedEntry.authorsCommaSeparated
        )
      }
    }
}
