package org.nypl.simplified.tests.books.accounts

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.nypl.drm.core.AdobeDeviceID
import org.nypl.drm.core.AdobeUserID
import org.nypl.drm.core.AdobeVendorID
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobeClientToken
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobePostActivationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobePreActivationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationTokenInfo
import org.nypl.simplified.accounts.api.AccountCookie
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.accounts.json.AccountAuthenticationCredentialsJSON
import org.nypl.simplified.accounts.json.AccountAuthenticationCredentialsJSON.deserializeFromJSON
import org.nypl.simplified.accounts.json.AccountAuthenticationCredentialsJSON.serializeToJSON
import org.nypl.simplified.patron.api.PatronAuthorization
import java.net.URI

/**
 * @see AccountAuthenticationCredentialsJSON
 */

class AccountAuthenticationCredentialsJSONTest {

  @Test
  @Throws(Exception::class)
  fun testRoundTrip0() {
    val creds0: AccountAuthenticationCredentials =
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("1234"),
        password = AccountPassword("5678"),
        adobeCredentials = null,
        authenticationDescription = null,
        annotationsURI = URI("https://www.example.com"),
        deviceRegistrationURI = URI("https://www.example.com"),
        patronAuthorization = PatronAuthorization("identifier", null)
      )

    val creds1 = deserializeFromJSON(serializeToJSON(creds0))
    Assertions.assertEquals(creds0, creds1)
  }

  @Test
  @Throws(Exception::class)
  fun testRoundTrip2() {
    val adobe =
      AccountAuthenticationAdobePreActivationCredentials(
        vendorID = AdobeVendorID("vendor"),
        clientToken = AccountAuthenticationAdobeClientToken.parse("NYNYPL|156|5e0cdf28-e3a2-11e7-ab18-0e26ed4612aa|LEcBeSV"),
        deviceManagerURI = URI.create("http://example.com"),
        postActivationCredentials = null
      )

    val creds0: AccountAuthenticationCredentials =
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("1234"),
        password = AccountPassword("5678"),
        adobeCredentials = adobe,
        authenticationDescription = null,
        annotationsURI = URI("https://www.example.com"),
        deviceRegistrationURI = URI("https://www.example.com"),
        patronAuthorization = PatronAuthorization("identifier", null)
      )

    val creds1 = deserializeFromJSON(serializeToJSON(creds0))
    Assertions.assertEquals(creds0, creds1)
  }

  @Test
  @Throws(Exception::class)
  fun testRoundTrip3() {
    val post =
      AccountAuthenticationAdobePostActivationCredentials(
        deviceID = AdobeDeviceID("device"),
        userID = AdobeUserID("user")
      )
    val adobe =
      AccountAuthenticationAdobePreActivationCredentials(
        vendorID = AdobeVendorID("vendor"),
        clientToken = AccountAuthenticationAdobeClientToken.parse("NYNYPL|156|5e0cdf28-e3a2-11e7-ab18-0e26ed4612aa|LEcBeSV"),
        deviceManagerURI = URI.create("http://example.com"),
        postActivationCredentials = post
      )

    val creds0: AccountAuthenticationCredentials =
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("1234"),
        password = AccountPassword("5678"),
        adobeCredentials = adobe,
        authenticationDescription = "fake",
        annotationsURI = URI("https://www.example.com"),
        deviceRegistrationURI = URI("https://www.example.com"),
        patronAuthorization = PatronAuthorization("identifier", null)
      )

    val creds1 = deserializeFromJSON(serializeToJSON(creds0))
    Assertions.assertEquals(creds0, creds1)
  }

  @Test
  @Throws(Exception::class)
  fun testRoundTrip4() {
    val post =
      AccountAuthenticationAdobePostActivationCredentials(
        deviceID = AdobeDeviceID("device"),
        userID = AdobeUserID("user")
      )
    val adobe =
      AccountAuthenticationAdobePreActivationCredentials(
        vendorID = AdobeVendorID("vendor"),
        clientToken = AccountAuthenticationAdobeClientToken.parse("NYNYPL|156|5e0cdf28-e3a2-11e7-ab18-0e26ed4612aa|LEcBeSV"),
        deviceManagerURI = URI.create("http://example.com"),
        postActivationCredentials = post
      )

    val creds0: AccountAuthenticationCredentials =
      AccountAuthenticationCredentials.SAML2_0(
        accessToken = "76885cd7-f2e9-4930-a9a1-1ea8f1093ed9",
        adobeCredentials = adobe,
        authenticationDescription = "fake",
        annotationsURI = URI("https://www.example.com"),
        deviceRegistrationURI = URI("https://www.example.com"),
        patronAuthorization = PatronAuthorization("identifier", null),
        patronInfo = "{}",
        cookies = listOf(
          AccountCookie("https://example", "cookie0=23"),
          AccountCookie("https://fake", "cookie1=24; Path=/; Secure"),
          AccountCookie("http://something", "cookie2=25; Path=/abc; Expires=Wed, 23 Dec 2020 07:28:00 GMT")
        )
      )

    val creds1 = deserializeFromJSON(serializeToJSON(creds0))
    Assertions.assertEquals(creds0, creds1)
  }

  @Test
  @Throws(Exception::class)
  fun testRoundTrip5() {
    val creds0: AccountAuthenticationCredentials =
      AccountAuthenticationCredentials.BasicToken(
        userName = AccountUsername("1234"),
        password = AccountPassword("5678"),
        authenticationTokenInfo = AccountAuthenticationTokenInfo(
          accessToken = "abcd",
          authURI = URI("https://www.authrefresh.com")
        ),
        adobeCredentials = null,
        authenticationDescription = null,
        annotationsURI = URI("https://www.example.com"),
        deviceRegistrationURI = URI("https://www.example.com"),
        patronAuthorization = PatronAuthorization("identifier", null)
      )

    val creds1 = deserializeFromJSON(serializeToJSON(creds0))
    Assertions.assertEquals(creds0, creds1)
  }

  @Test
  @Throws(Exception::class)
  fun testRoundTrip6() {
    val adobe =
      AccountAuthenticationAdobePreActivationCredentials(
        vendorID = AdobeVendorID("vendor"),
        clientToken = AccountAuthenticationAdobeClientToken.parse("NYNYPL|156|5e0cdf28-e3a2-11e7-ab18-0e26ed4612aa|LEcBeSV"),
        deviceManagerURI = URI.create("http://example.com"),
        postActivationCredentials = null
      )

    val creds0: AccountAuthenticationCredentials =
      AccountAuthenticationCredentials.BasicToken(
        userName = AccountUsername("1234"),
        password = AccountPassword("5678"),
        authenticationTokenInfo = AccountAuthenticationTokenInfo(
          accessToken = "abcd",
          authURI = URI("https://www.authrefresh.com")
        ),
        adobeCredentials = adobe,
        authenticationDescription = null,
        annotationsURI = URI("https://www.example.com"),
        deviceRegistrationURI = URI("https://www.example.com"),
        patronAuthorization = PatronAuthorization("identifier", null)
      )

    val creds1 = deserializeFromJSON(serializeToJSON(creds0))
    Assertions.assertEquals(creds0, creds1)
  }

  @Test
  @Throws(Exception::class)
  fun testRoundTrip7() {
    val post =
      AccountAuthenticationAdobePostActivationCredentials(
        deviceID = AdobeDeviceID("device"),
        userID = AdobeUserID("user")
      )
    val adobe =
      AccountAuthenticationAdobePreActivationCredentials(
        vendorID = AdobeVendorID("vendor"),
        clientToken = AccountAuthenticationAdobeClientToken.parse("NYNYPL|156|5e0cdf28-e3a2-11e7-ab18-0e26ed4612aa|LEcBeSV"),
        deviceManagerURI = URI.create("http://example.com"),
        postActivationCredentials = post
      )

    val creds0: AccountAuthenticationCredentials =
      AccountAuthenticationCredentials.BasicToken(
        userName = AccountUsername("1234"),
        password = AccountPassword("5678"),
        authenticationTokenInfo = AccountAuthenticationTokenInfo(
          accessToken = "abcd",
          authURI = URI("https://www.authrefresh.com")
        ),
        adobeCredentials = adobe,
        authenticationDescription = null,
        annotationsURI = URI("https://www.example.com"),
        deviceRegistrationURI = URI("https://www.example.com"),
        patronAuthorization = PatronAuthorization("identifier", null)
      )

    val creds1 = deserializeFromJSON(serializeToJSON(creds0))
    Assertions.assertEquals(creds0, creds1)
  }

  /**
   * A recorded ADEPT provider version survives a serialization round-trip.
   */

  @Test
  @Throws(Exception::class)
  fun testRoundTrip8() {
    val post =
      AccountAuthenticationAdobePostActivationCredentials(
        deviceID = AdobeDeviceID("device"),
        userID = AdobeUserID("user"),
        version = "5.0.0"
      )
    val adobe =
      AccountAuthenticationAdobePreActivationCredentials(
        vendorID = AdobeVendorID("vendor"),
        clientToken = AccountAuthenticationAdobeClientToken.parse("NYNYPL|156|5e0cdf28-e3a2-11e7-ab18-0e26ed4612aa|LEcBeSV"),
        deviceManagerURI = URI.create("http://example.com"),
        postActivationCredentials = post
      )

    val creds0: AccountAuthenticationCredentials =
      AccountAuthenticationCredentials.Basic(
        userName = AccountUsername("1234"),
        password = AccountPassword("5678"),
        adobeCredentials = adobe,
        authenticationDescription = null,
        annotationsURI = URI("https://www.example.com"),
        deviceRegistrationURI = URI("https://www.example.com"),
        patronAuthorization = PatronAuthorization("identifier", null)
      )

    val creds1 = deserializeFromJSON(serializeToJSON(creds0))
    Assertions.assertEquals(creds0, creds1)
  }

  /**
   * A credentials document written before version recording has no `version` field in the
   * post-activation block. It must deserialize with a null version.
   */

  @Test
  @Throws(Exception::class)
  fun testOldDocumentWithoutVersion() {
    val json =
      """
{
  "@version": 20210512,
  "@type": "basic",
  "username": "1234",
  "password": "5678",
  "annotationsURI": "https://www.example.com",
  "deviceRegistrationURI": "https://www.example.com",
  "adobe_credentials": {
    "client_token": "NYNYPL|156|5e0cdf28-e3a2-11e7-ab18-0e26ed4612aa|LEcBeSV",
    "vendor_id": "vendor",
    "activation": {
      "device_id": "device",
      "user_id": "user"
    }
  }
}
      """.trimIndent()

    val creds =
      deserializeFromJSON(ObjectMapper().readTree(json)) as AccountAuthenticationCredentials.Basic
    val post = creds.adobeCredentials!!.postActivationCredentials!!
    Assertions.assertEquals(AdobeDeviceID("device"), post.deviceID)
    Assertions.assertEquals(AdobeUserID("user"), post.userID)
    Assertions.assertNull(post.version)
  }
}
