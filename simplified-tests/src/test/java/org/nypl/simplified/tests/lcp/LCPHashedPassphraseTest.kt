package org.nypl.simplified.tests.lcp

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.nypl.simplified.lcp.LCPHashedPassphrase

class LCPHashedPassphraseTest {

  @Test
  fun isBase64Encoded_isFalse_whenHashedPassphraseIsUnencoded() {
    Assertions.assertFalse(
      LCPHashedPassphrase.isBase64Encoded("ecf294243697a2bb915bc78577f49d6c37b8b0dc6bb4c6bd1bc425dfad8317ae")
    )
  }

  @Test
  fun isBase64Encoded_isTrue_whenHashedPassphraseIsEncoded() {
    Assertions.assertTrue(
      LCPHashedPassphrase.isBase64Encoded("SjyZbIt/wsNT5YQ33+x0fp7p4NlxG3S8n/YICpJM688=")
    )
  }

  @Test
  fun isBase64Encoded_isTrue_whenHashedPassphraseIsNot64Chars() {
    Assertions.assertTrue(
      LCPHashedPassphrase.isBase64Encoded("ecf294243697a2bb915bc78577f49d6c37b8b0dc6bb4c6bd1bc425dfad8317a")
    )
  }

  @Test
  fun isBase64Encoded_isTrue_whenHashedPassphraseContainsNonHexChar() {
    Assertions.assertTrue(
      LCPHashedPassphrase.isBase64Encoded("ecf294243697a2bb915bc78577f49d6c3/b8b0dc6bb4c6bd1bc425dfad8317ae")
    )
  }

  @Test
  fun base64Encode_returnsEncodedValue() {
    Assertions.assertEquals(
      "SjyZbIt/wsNT5YQ33+x0fp7p4NlxG3S8n/YICpJM688=",
      LCPHashedPassphrase.base64Encode("4a3c996c8b7fc2c353e58437dfec747e9ee9e0d9711b74bc9ff6080a924cebcf")
    )
  }

  @Test
  fun base64Decode_returnsDecodedValue() {
    Assertions.assertEquals(
      "4a3c996c8b7fc2c353e58437dfec747e9ee9e0d9711b74bc9ff6080a924cebcf",
      LCPHashedPassphrase.base64Decode("SjyZbIt/wsNT5YQ33+x0fp7p4NlxG3S8n/YICpJM688=")
    )
  }

  @Test
  fun conditionallyBase64Decode_returnsDecodedValue_whenHashedPassphraseIsEncoded() {
    Assertions.assertEquals(
      "4a3c996c8b7fc2c353e58437dfec747e9ee9e0d9711b74bc9ff6080a924cebcf",
      LCPHashedPassphrase.conditionallyBase64Decode("SjyZbIt/wsNT5YQ33+x0fp7p4NlxG3S8n/YICpJM688=")
    )
  }

  @Test
  fun conditionallyBase64Decode_returnsInputValue_whenHashedPassphraseIsUnencoded() {
    Assertions.assertEquals(
      "4a3c996c8b7fc2c353e58437dfec747e9ee9e0d9711b74bc9ff6080a924cebcf",
      LCPHashedPassphrase.conditionallyBase64Decode("4a3c996c8b7fc2c353e58437dfec747e9ee9e0d9711b74bc9ff6080a924cebcf")
    )
  }
}
