package org.nypl.simplified.books.api

import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Functions to construct book IDs.
 */
object BookIDs {
  /**
   * Construct a book ID derived from the hash of the given text.
   *
   * @param text The text
   * @return A new book ID
   */
  @JvmStatic
  fun newFromText(text: String): BookID {
    try {
      val md = MessageDigest.getInstance("SHA-256")
      md.update(text.toByteArray())
      val dg = md.digest()

      val b = StringBuilder(64)
      for (index in dg.indices) {
        val bb = dg[index]
        b.append(String.format("%02x", bb))
      }

      return BookID.create(b.toString())
    } catch (e: NoSuchAlgorithmException) {
      throw IllegalStateException(e)
    }
  }

  /**
   * Calculate a book ID from the given acquisition feed entry.
   *
   * @param e The entry
   *
   * @return A new book ID
   */
  @JvmStatic
  fun newFromOPDSEntry(e: OPDSAcquisitionFeedEntry): BookID = newFromText(e.id)
}
