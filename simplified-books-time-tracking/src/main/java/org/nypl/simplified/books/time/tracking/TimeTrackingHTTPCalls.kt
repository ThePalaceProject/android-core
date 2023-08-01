package org.nypl.simplified.books.time.tracking

import com.fasterxml.jackson.databind.ObjectMapper
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI

/**
 * A minimal class around the various annotations and user profile REST calls.
 */

class TimeTrackingHTTPCalls(
  private val objectMapper: ObjectMapper,
  private val http: LSHTTPClientType
) : TimeTrackingHTTPCallsType {

  private val logger = LoggerFactory.getLogger(TimeTrackingHTTPCalls::class.java)

  override fun registerTimeTrackingInfo(
    timeTrackingInfo: TimeTrackingInfo,
    credentials: AccountAuthenticationCredentials?
  ): List<TimeTrackingEntry> {

    credentials ?: throw(Exception("Invalid Credentials"))
    timeTrackingInfo.timeTrackingUri ?: throw(Exception("Invalid URI"))

    val data =
      TimeTrackingJSON.serializeTimeTrackingInfoToBytes(this.objectMapper, timeTrackingInfo)
    val auth =
      AccountAuthenticatedHTTP.createAuthorization(credentials)
    val post =
      LSHTTPRequestBuilderType.Method.Post(
        data, MIMEType("application", "ld+json", mapOf())
      )
    val request =
      this.http.newRequest(timeTrackingInfo.timeTrackingUri)
        .setAuthorization(auth)
        .setMethod(post)
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          objectMapper.readTree(
            status.bodyStream ?: ByteArrayInputStream(ByteArray(0))
          )
          listOf()
        }
        is LSHTTPResponseStatus.Responded.Error -> {
          logAndFail(timeTrackingInfo.timeTrackingUri, status)
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
