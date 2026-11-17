package org.nypl.simplified.feeds.api

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Functions to construct feed book IDs.
 */
object FeedBookIDs {
  /**
   * Construct a feed book ID derived from the hash of the given text.
   *
   * @param text The text
   * @return A new feed book ID
   */
  fun newFromText(text: String): FeedBookID {
    try {
      val md = MessageDigest.getInstance("SHA-256")
      md.update(text.toByteArray(StandardCharsets.UTF_8))
      val dg = md.digest()

      val b = StringBuilder(64)
      for (bb in dg) {
        b.append(String.format("%02x", bb))
      }

      return FeedBookID.create(b.toString())
    } catch (e: NoSuchAlgorithmException) {
      throw IllegalStateException(e)
    }
  }
}
