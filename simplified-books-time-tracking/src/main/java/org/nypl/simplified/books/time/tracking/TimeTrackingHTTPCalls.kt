package org.nypl.simplified.books.time.tracking

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.crashlytics.api.CrashlyticsServiceType
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

  private val services =
    Services.serviceDirectory()

  private val crashlytics =
    this.services.optionalService(CrashlyticsServiceType::class.java)

  private val logger = LoggerFactory.getLogger(TimeTrackingHTTPCalls::class.java)

  override fun registerTimeTrackingInfo(
    timeTrackingInfo: TimeTrackingInfo,
    credentials: AccountAuthenticationCredentials?
  ): List<TimeTrackingEntry> {

    credentials ?: throw(Exception("Invalid Credentials"))

    val data =
      TimeTrackingJSON.convertTimeTrackingInfoToBytes(this.objectMapper, timeTrackingInfo)
    val auth =
      AccountAuthenticatedHTTP.createAuthorization(credentials)
    val post =
      LSHTTPRequestBuilderType.Method.Post(
        data, MIMEType("application", "json", mapOf())
      )
    val request =
      this.http.newRequest(timeTrackingInfo.timeTrackingUri)
        .setAuthorization(auth)
        .setMethod(post)
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          val timeTrackingResponse = TimeTrackingJSON.convertServerResponseToTimeTrackingResponse(
            objectNode = objectMapper.readTree(
              status.bodyStream ?: ByteArrayInputStream(ByteArray(0))
            ) as ObjectNode
          )

          val summary = timeTrackingResponse?.summary
          val responses = timeTrackingResponse?.responses.orEmpty()

          logger.debug(
            "Received time tracking summary: {} successes + {} failures = {} total",
            summary?.successes,
            summary?.failures,
            summary?.total
          )

          if (responses.isNotEmpty()) {
            timeTrackingInfo.timeEntries.filter { timeEntry ->
              val responseEntry = responses.firstOrNull { response ->
                response.id == timeEntry.id
              } ?: return@filter false

              val hasFailed = !responseEntry.isStatusSuccess() && !responseEntry.isStatusGone()

              if (!hasFailed) {
                crashlytics?.log("Failed entry received from server: [id: ${responseEntry.id}, " +
                  "message: ${responseEntry.message}, status: ${responseEntry.status}]")
              }

              hasFailed
            }
          } else {
            // return the original time entries if the server response has no response entries
            timeTrackingInfo.timeEntries
          }
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
