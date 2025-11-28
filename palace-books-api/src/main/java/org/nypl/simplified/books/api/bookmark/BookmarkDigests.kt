package org.nypl.simplified.books.api.bookmark

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

object BookmarkDigests {
  fun addToDigest(
    digest: MessageDigest,
    text: String
  ) {
    digest.update(text.toByteArray(UTF_8))
  }

  fun addToDigest(
    digest: MessageDigest,
    number: Int
  ) {
    digest.update(number.toString().toByteArray(UTF_8))
  }

  fun addToDigest(
    digest: MessageDigest,
    number: Double
  ) {
    addToDigest(digest, String.format("%.6f", number))
  }
}
