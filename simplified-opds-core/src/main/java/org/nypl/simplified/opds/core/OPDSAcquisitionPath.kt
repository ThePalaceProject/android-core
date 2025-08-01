package org.nypl.simplified.opds.core

import one.irradia.mime.api.MIMEType
import org.nypl.simplified.links.Link

/**
 * A linearized OPDS acquisition path.
 *
 * @see "https://github.com/io7m/opds-acquisition-spec"
 */

data class OPDSAcquisitionPath(
  val source: OPDSAcquisition,
  val elements: List<OPDSAcquisitionPathElement>
) {

  /**
   * Prefix the path with the given MIME and URI.
   */

  fun prefixWith(
    mime: MIMEType,
    target: Link?,
    properties: Map<String, String>
  ): OPDSAcquisitionPath {
    val newElements = mutableListOf<OPDSAcquisitionPathElement>()
    newElements.add(OPDSAcquisitionPathElement(mime, target, properties))
    newElements.addAll(this.elements)
    return OPDSAcquisitionPath(this.source, newElements.toList())
  }

  /**
   * @return This path as a series of MIME types
   */

  fun asMIMETypes(): List<MIMEType> {
    val types = mutableListOf<MIMEType>()
    types.add(this.source.type)
    for (element in this.elements) {
      types.add(element.mimeType)
    }
    return types.toList()
  }
}
