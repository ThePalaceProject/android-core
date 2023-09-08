package org.nypl.simplified.feeds.api

import one.irradia.mime.api.MIMECompatibility
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addCredentialsToProperties
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.getAccessToken
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.opds.core.OPDSFeedTransportIOException
import org.nypl.simplified.opds.core.OPDSFeedTransportType
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.Locale

/**
 * An implementation of the [OPDSFeedTransportType] interface that uses an
 * [HTTPType] instance for communication, supporting optional
 * authentication.
 */

class FeedHTTPTransport(
  private val http: LSHTTPClientType
) : OPDSFeedTransportType<AccountAuthenticationCredentials?> {

  private val logger =
    LoggerFactory.getLogger(FeedHTTPTransport::class.java)

  override fun getStream(
    credentials: AccountAuthenticationCredentials?,
    uri: URI,
    method: String
  ): Pair<InputStream, String?> {
    this.logger.debug("get stream: {} {}", uri, credentials)

    val auth = AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials)

    val request =
      this.http.newRequest(uri)
        .setAuthorization(auth)
        .addCredentialsToProperties(credentials)
        .setMethod(this.methodOfName(method))
        .build()

    val response = request.execute()
    return when (val status = response.status) {
      is LSHTTPResponseStatus.Responded.OK -> {
        (status.bodyStream ?: ByteArrayInputStream(ByteArray(0))) to status.getAccessToken()
      }
      is LSHTTPResponseStatus.Responded.Error ->
        throw FeedHTTPTransportException(
          message = status.properties.message,
          code = status.properties.status,
          report = status.properties.problemReport
        )

      is LSHTTPResponseStatus.Failed ->
        throw OPDSFeedTransportIOException(
          message = "Connection failed",
          cause = IOException(status.exception)
        )
    }
  }

  private fun methodOfName(method: String): LSHTTPRequestBuilderType.Method {
    return when (method.uppercase(Locale.ROOT)) {
      "GET" -> {
        LSHTTPRequestBuilderType.Method.Get
      }
      "HEAD" -> {
        LSHTTPRequestBuilderType.Method.Head
      }
      "POST" -> {
        LSHTTPRequestBuilderType.Method.Post(ByteArray(0), MIMECompatibility.applicationOctetStream)
      }
      "PUT" -> {
        LSHTTPRequestBuilderType.Method.Put(ByteArray(0), MIMECompatibility.applicationOctetStream)
      }
      "DELETE" -> {
        LSHTTPRequestBuilderType.Method.Delete(ByteArray(0), MIMECompatibility.applicationOctetStream)
      }
      else -> {
        throw IllegalArgumentException("Unsupported request method: $method")
      }
    }
  }
}
