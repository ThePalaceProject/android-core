package org.nypl.simplified.feeds.api

import java.util.regex.Pattern

/**
 * The unique identifier for a book within a feed. This is typically a SHA256 hash of the
 * original book URI and is intended to be safe for use as a file or directory name.
 */
data class FeedBookID internal constructor(
  /**
   * The actual ID value.
   */
  val value: String,
) : Comparable<FeedBookID> {
  override fun compareTo(other: FeedBookID): Int {
    return this.value.compareTo(other.value)
  }

  /**
   * The brief form of the ID.
   */
  fun brief(): String {
    return this.value.substring(0, Math.min(8, this.value.length))
  }

  override fun toString(): String {
    return this.value
  }

  companion object {
    /**
     * The regular expression that defines a valid feed book ID.
     */
    val VALID_FEED_BOOK_ID: Pattern = Pattern.compile("[a-z0-9]+")

    /**
     * Construct a feed book ID.
     *
     * @param value The raw feed book ID value
     */
    fun create(value: String): FeedBookID {
      val matches = VALID_FEED_BOOK_ID.matcher(value).matches()
      if (!matches) {
        throw IllegalArgumentException(
          "Feed book IDs must be non-empty, alphanumeric lowercase (${VALID_FEED_BOOK_ID.pattern()})",
        )
      }
      return FeedBookID(value)
    }
  }
}
