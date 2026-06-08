package org.nypl.simplified.opds.core

import org.w3c.dom.Element
import java.io.InputStream
import java.net.URI

/**
 * The type of feed entry parsers.
 */
interface OPDSAcquisitionFeedEntryParserType {
  /**
   * Parse the feed entry represented by the XML element `e`.
   *
   * @param source The source URI
   * @param element      The XML element
   * @return A parsed feed entry
   * @throws OPDSParseException On errors
   */
  @Throws(OPDSParseException::class)
  fun parseEntry(
    source: URI,
    element: Element
  ): OPDSAcquisitionFeedEntry

  /**
   * Parse the feed entry represented by the given stream `s`.
   *
   * @param source The source URI
   * @param stream      The entry stream
   * @return A parsed feed entry
   * @throws OPDSParseException On errors
   */
  @Throws(OPDSParseException::class)
  fun parseEntryStream(
    source: URI,
    stream: InputStream
  ): OPDSAcquisitionFeedEntry
}
