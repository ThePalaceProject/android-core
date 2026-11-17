package org.nypl.simplified.feeds.api

import org.librarysimplified.http.api.LSHTTPProblemReport

/**
 * An exception caused by an HTTP transport error during feed loading.
 *
 * @param message The message.
 * @param code The HTTP status code.
 * @param report The problem report, if one was returned by the server.
 */
class FeedHTTPTransportException(
  message: String,
  val code: Int,
  val report: LSHTTPProblemReport?,
) : Exception(message)
