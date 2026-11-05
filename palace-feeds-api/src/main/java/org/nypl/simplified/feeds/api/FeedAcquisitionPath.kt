package org.nypl.simplified.feeds.api

import one.irradia.mime.api.MIMEType
import org.nypl.simplified.links.Link

/**
 * A linearized acquisition path.
 */
data class FeedAcquisitionPath(
  val source: FeedAcquisition,
  val elements: List<FeedAcquisitionPathElement>
) {
  /**
   * Prefix the path with the given MIME type and target.
   */
  fun prefixWith(
    mime: MIMEType,
    target: Link?,
    properties: Map<String, String>
  ): FeedAcquisitionPath {
    val newElements = mutableListOf<FeedAcquisitionPathElement>()
    newElements.add(FeedAcquisitionPathElement(mime, target, properties))
    newElements.addAll(this.elements)
    return FeedAcquisitionPath(this.source, newElements.toList())
  }

  /**
   * @return This path as a series of MIME types.
   */
  fun asMIMETypes(): List<MIMEType> {
    val types = mutableListOf<MIMEType>()
    types.add(this.source.type)
    for (element in this.elements) {
      types.add(element.mimeType)
    }
    return types
  }
}
