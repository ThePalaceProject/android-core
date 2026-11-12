package org.nypl.simplified.feeds.api

import one.irradia.mime.api.MIMEType
import java.io.InputStream

/**
 * The result of fetching feed content.
 */

data class FeedFetchResult(
  val body: InputStream,
  val accessToken: String?,
  val contentType: MIMEType?,
)
