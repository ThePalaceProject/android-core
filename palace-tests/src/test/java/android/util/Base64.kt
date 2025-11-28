package android.util

import java.util.Base64

/**
 * A mock of android.util.Base64 to use in tests, when the Android API is not available.
 */

object Base64 {

  @JvmStatic
  fun encodeToString(input: ByteArray?, flags: Int): String? {
    return Base64.getEncoder().encodeToString(input)
  }

  @JvmStatic
  fun decode(str: String?, flags: Int): ByteArray? {
    return Base64.getDecoder().decode(str)
  }
}
