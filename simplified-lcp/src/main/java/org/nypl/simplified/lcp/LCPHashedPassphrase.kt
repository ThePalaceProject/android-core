package org.nypl.simplified.lcp

import android.util.Base64

/**
 * Functions for working with LCP hashed passphrases.
 */

object LCPHashedPassphrase {

  /**
   * Determine if a hashed passphrase is base-64 encoded. An unencoded hashed passphrase is a
   * 64-character hex string, so if the value is not 64 characters long, or contains a character
   * that is not a hexadecimal digit, it is considered to be encoded.
   */

  @JvmStatic
  fun isBase64Encoded(hashedPassphrase: String): Boolean =
    hashedPassphrase.length != 64 || hashedPassphrase.contains(Regex("[^0-9a-fA-F]"))

  /**
   * Base-64 encode a hashed passphrase, using the algorithm described in the LCP spec:
   * https://readium.org/lcp-specs/notes/lcp-key-retrieval.html#sample-of-readium-web-publication-manifest-supporting-a-link-to-an-lcp-license-and-an-lcp_hashed_passphrase-property
   */

  @JvmStatic
  fun base64Encode(hashedPassphrase: String): String {
    val bytes = hashedPassphrase.chunked(2)
      .map { it.toInt(16).toByte() }
      .toByteArray()

    return Base64.encodeToString(bytes, Base64.NO_WRAP)
  }

  /**
   * Decode a base-64 encoded hashed passphrase, using the algorithm described in the LCP spec:
   * https://readium.org/lcp-specs/notes/lcp-key-retrieval.html#sample-of-readium-web-publication-manifest-supporting-a-link-to-an-lcp-license-and-an-lcp_hashed_passphrase-property
   */

  @JvmStatic
  fun base64Decode(encodedHashedPassphrase: String) =
    Base64.decode(encodedHashedPassphrase, Base64.DEFAULT)
      .joinToString(separator = "") { "%02x".format(it) }

  /**
   * Determine if a hashed passphrase is base-64 encoded, and decode it if necessary.
   */

  @JvmStatic
  fun conditionallyBase64Decode(hashedPassphrase: String) =
    if (isBase64Encoded(hashedPassphrase))
      base64Decode(hashedPassphrase)
    else
      hashedPassphrase
}
