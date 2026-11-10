package org.nypl.simplified.feeds.opds12

import org.librarysimplified.http.api.LSHTTPProblemReport
import org.nypl.simplified.opds.core.OPDSFeedTransportException

/**
 * The type of exceptions caused by HTTP errors in OPDS 1.2 feed loading.
 */
class OPDS12FeedHTTPTransportException(
  override val message: String,
  val code: Int,
  val report: LSHTTPProblemReport?,
) : OPDSFeedTransportException(message)
