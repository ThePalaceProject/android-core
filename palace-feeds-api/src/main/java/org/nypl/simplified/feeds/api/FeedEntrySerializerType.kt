package org.nypl.simplified.feeds.api

import java.io.IOException

/**
 * The type of serializers that produce JSON from a [FeedEntryBook].
 */
interface FeedEntrySerializerType {
  /**
   * Serialize the given feed entry to a JSON string.
   *
   * @param entry The feed entry.
   * @return The JSON string.
   * @throws IOException On serialization errors.
   */
  @Throws(IOException::class)
  fun serializeEntry(entry: FeedEntryBook): String
}
