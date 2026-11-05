package org.nypl.simplified.feeds.api

import java.net.URI

/**
 * The relation type of a feed acquisition.
 */
enum class FeedAcquisitionRelation(val uri: URI) {
  ACQUISITION_BORROW(URI.create("http://opds-spec.org/acquisition/borrow")),
  ACQUISITION_BUY(URI.create("http://opds-spec.org/acquisition/buy")),
  ACQUISITION_GENERIC(URI.create("http://opds-spec.org/acquisition")),
  ACQUISITION_OPEN_ACCESS(URI.create("http://opds-spec.org/acquisition/open-access")),
  ACQUISITION_SAMPLE(URI.create("http://opds-spec.org/acquisition/sample")),
  ACQUISITION_SUBSCRIBE(URI.create("http://opds-spec.org/acquisition/subscribe"))
}
