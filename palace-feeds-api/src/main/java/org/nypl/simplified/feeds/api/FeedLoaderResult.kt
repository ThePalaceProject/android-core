package org.nypl.simplified.feeds.api

import org.librarysimplified.http.api.LSHTTPProblemReport
import org.nypl.simplified.presentableerror.api.PresentableErrorType
import org.nypl.simplified.presentableerror.api.Presentables
import java.net.URI

/**
 * The result of loading a feed.
 */

sealed class FeedLoaderResult {
  /**
   * The feed was loaded successfully.
   */

  data class FeedLoaderSuccess(
    val feed: Feed,
    val accessToken: String?,
  ) : FeedLoaderResult()

  /**
   * The feed failed to load.
   */

  sealed class FeedLoaderFailure :
    FeedLoaderResult(),
    PresentableErrorType {
    /**
     * The feed failed to load due to the given exception.
     */

    data class FeedLoaderFailedGeneral(
      val problemReport: LSHTTPProblemReport?,
      override val exception: Exception,
      override val message: String,
      private val attributesInitial: Map<String, String>,
    ) : FeedLoaderFailure() {
      override val attributes: Map<String, String>
        get() {
          val reportMap: Map<String, String>
          val report = this.problemReport
          if (report == null) {
            reportMap = emptyMap()
          } else {
            reportMap = report.toMap()
          }
          return Presentables.mergeAttributes(
            map0 = this.attributesInitial,
            map1 = reportMap,
          )
        }
    }

    /**
     * The feed failed to load due to an authentication error.
     */

    data class FeedLoaderFailedAuthentication(
      val problemReport: LSHTTPProblemReport?,
      override val exception: Exception,
      override val message: String,
      private val attributesInitial: Map<String, String>,
    ) : FeedLoaderFailure() {
      override val attributes: Map<String, String>
        get() {
          val reportMap: Map<String, String>
          val report = this.problemReport
          if (report == null) {
            reportMap = emptyMap()
          } else {
            reportMap = report.toMap()
          }
          return Presentables.mergeAttributes(
            map0 = this.attributesInitial,
            map1 = reportMap,
          )
        }
    }
  }

  companion object {
    /**
     * Wrap an exception, producing a general feed loading error.
     */

    fun wrapException(
      uri: URI,
      exception: Throwable,
    ): FeedLoaderFailure {
      val wrappedException: java.lang.Exception
      if (exception is java.lang.Exception) {
        wrappedException = exception
      } else {
        wrappedException = java.lang.Exception(exception)
      }

      val message: String
      val localizedMessage = exception.localizedMessage
      if (localizedMessage == null) {
        message = ""
      } else {
        message = localizedMessage
      }

      return FeedLoaderFailure.FeedLoaderFailedGeneral(
        problemReport = null,
        exception = wrappedException,
        message = message,
        attributesInitial = sortedMapOf(Pair("Feed URI", uri.toASCIIString())),
      )
    }
  }
}
