package org.nypl.simplified.tests.lcp

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.nypl.simplified.lcp.LCPContentProtectionProvider
import java.lang.IllegalStateException

class LCPContentProtectionProviderTest {

  @Test
  fun passphrase_returnsUnencodedValue_whenHashedPassphraseIsUnencoded() {
    val provider = LCPContentProtectionProvider().apply {
      passphrase = "4a3c996c8b7fc2c353e58437dfec747e9ee9e0d9711b74bc9ff6080a924cebcf"
    }

    Assertions.assertEquals(
      "4a3c996c8b7fc2c353e58437dfec747e9ee9e0d9711b74bc9ff6080a924cebcf",
      provider.passphrase()
    )
  }

  @Test
  fun passphrase_returnsUnencodedValue_whenHashedPassphraseIsEncoded() {
    val provider = LCPContentProtectionProvider().apply {
      passphrase = "SjyZbIt/wsNT5YQ33+x0fp7p4NlxG3S8n/YICpJM688="
    }

    Assertions.assertEquals(
      "4a3c996c8b7fc2c353e58437dfec747e9ee9e0d9711b74bc9ff6080a924cebcf",
      provider.passphrase()
    )
  }

  @Test
  fun passphrase_throws_whenHashedPassphraseIsNull() {
    val provider = LCPContentProtectionProvider().apply {
      passphrase = null
    }

    Assertions.assertThrows(IllegalStateException::class.java) {
      provider.passphrase()
    }
  }
}
