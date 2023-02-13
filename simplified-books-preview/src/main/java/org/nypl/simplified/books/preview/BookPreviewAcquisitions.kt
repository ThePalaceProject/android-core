package org.nypl.simplified.books.preview

import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSPreviewAcquisition

object BookPreviewAcquisitions {

  /**
   * Pick the preferred preview acquisition for the OPDS feed entry.
   */

  fun pickBestPreviewAcquisition(
    entry: OPDSAcquisitionFeedEntry
  ): OPDSPreviewAcquisition? {

    // we try to see if there's a preview acquisition of the "text/html" MIME type and if there
    // isn't one, we return the first preview acquisition that is on the list of supported book
    // previews, if any, or null, if none.
    return entry.previewAcquisitions.firstOrNull {
      it.type == StandardFormatNames.bookPreviewFiles.first()
    } ?: entry.previewAcquisitions.firstOrNull {
      StandardFormatNames.bookPreviewFiles.contains(it.type)
    }
  }
}
