package org.nypl.simplified.tests.books.accounts

import com.nimbusds.jwt.JWTParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.nypl.simplified.accounts.api.AccountOIDC
import java.net.URI
import java.nio.charset.StandardCharsets

class AccountOIDCTest {

  @Test
  fun testParse0() {
    val text = URI.create(resourceTextOf("callback0.txt"))
    val parsed = AccountOIDC.parseOIDCCallback(text)
    JWTParser.parse(parsed.accessToken)
  }

  @Test
  fun testParseError0() {
    val text = URI.create("palace-oidc-callback://org.thepalaceproject.oidc/account/x/?access_token=x")

    Assertions.assertThrows(IllegalArgumentException::class.java) {
      AccountOIDC.parseOIDCCallback(text)
    }
  }

  @Test
  fun testParseError1() {
    val text = URI.create("palace-oidc-callback://org.thepalaceproject.oidc/account/980b1d61-5617-4f36-bb27-4acee5b44ca5/")

    Assertions.assertThrows(IllegalArgumentException::class.java) {
      AccountOIDC.parseOIDCCallback(text)
    }
  }

  @Test
  fun testParseError2() {
    val text = URI.create("palace-oidc-callback://org.thepalaceproject.oidc/account/980b1d61-5617-4f36-bb27-4acee5b44ca5/?excess_torque=abcd")

    Assertions.assertThrows(IllegalArgumentException::class.java) {
      AccountOIDC.parseOIDCCallback(text)
    }
  }

  private fun resourceTextOf(
    name: String
  ): String {
    val path =
      "/org/nypl/simplified/tests/books/accounts/oidc/${name}"
    val stream =
      AccountOIDCTest::class.java.getResourceAsStream(path)!!
    return String(stream.readBytes(), StandardCharsets.UTF_8).trim()
  }
}
