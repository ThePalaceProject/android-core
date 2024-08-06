package org.nypl.simplified.books.preview

import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSPreviewAcquisition

object BookPreviewAcquisitions {

  /**
   * Pick the preferred preview acquisition for the OPDS feed entry. We use the first supported
   * acquisition, effectively allowing the server to set preferences.
   */

  fun pickBestPreviewAcquisition(
    entry: OPDSAcquisitionFeedEntry
  ): OPDSPreviewAcquisition? {
    return entry.previewAcquisitions.firstOrNull {
      StandardFormatNames.bookPreviewFiles.contains(it.type)
    }
  }
}
