package org.nypl.simplified.feeds.api

/**
 * The version of the OPDS protocol to request when fetching a feed.
 *
 * This replaces a raw `Accept` header string: the caller expresses which
 * protocol version it wants, and the fetch implementation decides which
 * request headers to send.
 */

enum class FeedFetchVersion {
  /** Request an OPDS 1.2 feed (no `Accept` header is sent). */
  OPDS_1_2,

  /** Request an OPDS 2.0 feed (an `Accept` header for the OPDS 2.0 media type is sent). */
  OPDS_2_0,
}
