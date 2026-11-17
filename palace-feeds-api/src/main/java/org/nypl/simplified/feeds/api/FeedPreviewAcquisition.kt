package org.nypl.simplified.feeds.api

import one.irradia.mime.api.MIMEType
import java.net.URI

/**
 * A preview acquisition for a feed entry.
 */
data class FeedPreviewAcquisition(
  val uri: URI,
  val type: MIMEType,
)
