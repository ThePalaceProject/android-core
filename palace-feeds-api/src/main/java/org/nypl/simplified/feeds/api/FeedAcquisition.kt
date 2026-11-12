package org.nypl.simplified.feeds.api

import one.irradia.mime.api.MIMEType
import org.nypl.simplified.links.Link

/**
 * A specific acquisition in a feed.
 */
data class FeedAcquisition(
  val relation: FeedAcquisitionRelation,
  val uri: Link,
  val type: MIMEType,
  val indirectAcquisitions: List<FeedIndirectAcquisition>,
  val properties: Map<String, String>,
) {
  /**
   * @return The set of final content types accessible if all acquisition paths are followed to their conclusions.
   */
  fun availableFinalContentTypes(): Set<MIMEType> {
    val set = mutableSetOf<MIMEType>()
    for (path in FeedAcquisitionPaths.linearize(this)) {
      set.add(path.elements.last().mimeType)
    }
    return set
  }
}
