package org.nypl.simplified.tests.books.accounts

import com.nimbusds.jwt.JWTParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.nypl.simplified.accounts.api.AccountOIDC
import java.net.URI
import java.nio.charset.StandardCharsets

class AccountOIDCTest {

  @Test
  fun testParseLogin0() {
    val text = URI.create(resourceTextOf("login-callback0.txt"))
    val parsed = AccountOIDC.parseOIDCCallback(text) as AccountOIDC.AccountOIDCParsedCallbackLogin
    assertEquals("8864fa60-6e5f-40f5-beb4-42f21c1fb139", parsed.account.uuid.toString())
    JWTParser.parse(parsed.accessToken)
  }

  @Test
  fun testParseLogout0() {
    val text = URI.create(resourceTextOf("logout-callback0.txt"))
    val parsed = AccountOIDC.parseOIDCCallback(text) as AccountOIDC.AccountOIDCParsedCallbackLogout
    assertEquals("8864fa60-6e5f-40f5-beb4-42f21c1fb139", parsed.account.uuid.toString())
  }

  @Test
  fun testParseLoginError0() {
    val text = URI.create("palace-oidc-callback://org.thepalaceproject.oidc/login/x/?access_token=x")

    Assertions.assertThrows(IllegalArgumentException::class.java) {
      AccountOIDC.parseOIDCCallback(text)
    }
  }

  @Test
  fun testParseLoginError1() {
    val text = URI.create("palace-oidc-callback://org.thepalaceproject.oidc/login/980b1d61-5617-4f36-bb27-4acee5b44ca5/")

    Assertions.assertThrows(IllegalArgumentException::class.java) {
      AccountOIDC.parseOIDCCallback(text)
    }
  }

  @Test
  fun testParseLoginError2() {
    val text = URI.create("palace-oidc-callback://org.thepalaceproject.oidc/login/980b1d61-5617-4f36-bb27-4acee5b44ca5/?excess_torque=abcd")

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
