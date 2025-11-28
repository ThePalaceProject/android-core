package org.nypl.simplified.books.borrowing

import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.opds.core.OPDSAcquisition
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionPath
import org.nypl.simplified.opds.core.OPDSAcquisitionPaths

/**
 * Functions to select book acquisitions based on the available format support.
 */

object BorrowAcquisitions {

  /**
   * Pick the preferred acquisition for the OPDS feed entry.
   */

  fun pickBestAcquisitionPath(
    support: BookFormatSupportType,
    entry: OPDSAcquisitionFeedEntry
  ): OPDSAcquisitionPath? {
    val paths = OPDSAcquisitionPaths.linearize(entry)
    return paths
      .filter { support.isSupportedPath(it.asMIMETypes()) }
      // when borrowing a book, we need to ignore if the acquisition is for a sample/preview
      .firstOrNull { it.source.relation != OPDSAcquisition.Relation.ACQUISITION_SAMPLE }
  }
}
