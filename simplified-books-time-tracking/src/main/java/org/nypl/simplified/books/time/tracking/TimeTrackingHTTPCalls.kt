package org.nypl.simplified.books.time.tracking

import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addCredentialsToProperties
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.getAccessToken
import org.nypl.simplified.accounts.database.api.AccountType
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI

/**
 * A minimal class around the various annotations and user profile REST calls.
 */

class TimeTrackingHTTPCalls(
  private val http: LSHTTPClientType
) : TimeTrackingHTTPCallsType {

  private val logger =
    LoggerFactory.getLogger(TimeTrackingHTTPCalls::class.java)

  override fun registerTimeTrackingInfo(
    request: TimeTrackingRequest,
    account: AccountType
  ): TimeTrackingServerResponse {
    val credentials =
      account.loginState.credentials ?: throw(Exception("Invalid Credentials"))

    val data =
      TimeTrackingJSON.serializeToBytes(request)
    val auth =
      AccountAuthenticatedHTTP.createAuthorization(credentials)
    val post =
      LSHTTPRequestBuilderType.Method.Post(
        data, MIMEType("application", "json", mapOf())
      )
    val httpRequest =
      this.http.newRequest(request.timeTrackingUri)
        .setAuthorization(auth)
        .addCredentialsToProperties(credentials)
        .setMethod(post)
        .build()

    return httpRequest.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          account.updateBasicTokenCredentials(status.getAccessToken())
          TimeTrackingJSON.deserializeResponse(
            status.bodyStream?.readBytes() ?: ByteArray(0)
          )
        }
        is LSHTTPResponseStatus.Responded.Error -> {
          logAndFail(request.timeTrackingUri, status)
        }
        is LSHTTPResponseStatus.Failed -> {
          throw status.exception
        }
      }
    }
  }

  private fun <T> logAndFail(
    uri: URI,
    error: LSHTTPResponseStatus.Responded.Error
  ): T {
    val problemReport = error.properties.problemReport
    if (problemReport != null) {
      this.logger.error("detail: {}", problemReport.detail)
      this.logger.error("status: {}", problemReport.status)
      this.logger.error("title:  {}", problemReport.title)
      this.logger.error("type:   {}", problemReport.type)
    }
    throw IOException("$uri received ${error.properties.status} ${error.properties.message}")
  }
}
