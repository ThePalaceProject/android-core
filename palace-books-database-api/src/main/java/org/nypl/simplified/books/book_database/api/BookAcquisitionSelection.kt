package org.nypl.simplified.books.book_database.api

import org.nypl.simplified.feeds.api.FeedAcquisition
import org.nypl.simplified.feeds.api.FeedAcquisitionRelation

/**
 * Functions to pick an acquisition based on the supported formats.
 */

object BookAcquisitionSelection {
  /**
   * Pick the preferred acquisition from the list of acquisitions. The selection is made
   * based on the supported relations of the acquisitions, and the available final content
   * types.
   *
   * @return A preferred acquisition, or nothing if none of the acquisitions were suitable
   */

  fun preferredAcquisition(acquisitions: List<FeedAcquisition>): FeedAcquisition? {
    val onlySupportedRelations: List<FeedAcquisition> =
      acquisitions.filter { acquisition -> relationIsSupported(acquisition) }

    for (acquisition in onlySupportedRelations) {
      val supportedContentTypes = BookFormats.supportedBookMimeTypes()
      val availableContentTypes = acquisition.availableFinalContentTypes()
      for (supportedType in supportedContentTypes) {
        for (availableType in availableContentTypes) {
          if (supportedType.fullType == availableType.fullType) {
            return acquisition
          }
        }
      }
    }

    return null
  }

  private fun relationIsSupported(acquisition: FeedAcquisition): Boolean =
    when (acquisition.relation) {
      FeedAcquisitionRelation.ACQUISITION_BORROW,
      FeedAcquisitionRelation.ACQUISITION_GENERIC,
      FeedAcquisitionRelation.ACQUISITION_OPEN_ACCESS -> true
      FeedAcquisitionRelation.ACQUISITION_BUY,
      FeedAcquisitionRelation.ACQUISITION_SAMPLE,
      FeedAcquisitionRelation.ACQUISITION_SUBSCRIBE -> false
    }
}
