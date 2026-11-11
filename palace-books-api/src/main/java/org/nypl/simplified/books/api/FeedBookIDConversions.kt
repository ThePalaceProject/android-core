package org.nypl.simplified.books.api

import org.nypl.simplified.feeds.api.FeedBookID

/**
 * Lossless conversions between [BookID] and [FeedBookID].
 */
object FeedBookIDConversions {
  /**
   * Convert a [BookID] to a [FeedBookID].
   */
  fun fromBookID(bookID: BookID): FeedBookID = FeedBookID.create(bookID.value())

  /**
   * Convert a [FeedBookID] to a [BookID].
   */
  fun toBookID(feedBookID: FeedBookID): BookID = BookID.create(feedBookID.value())
}
