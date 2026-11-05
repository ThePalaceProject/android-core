package org.nypl.simplified.feeds.api

import one.irradia.mime.api.MIMEType
import org.nypl.simplified.links.Link

/**
 * A single element in a linearized acquisition path.
 */
data class FeedAcquisitionPathElement(
  val mimeType: MIMEType,
  val target: Link?,
  val properties: Map<String, String>
)
