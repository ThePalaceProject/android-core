package org.nypl.simplified.books.borrowing.internal

import com.fasterxml.jackson.databind.ObjectMapper
import one.irradia.mime.api.MIMECompatibility
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.librarysimplified.http.bearer_token.LSSimplifiedBearerToken
import org.librarysimplified.http.bearer_token.LSSimplifiedBearerTokenObjectMappers
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.Handled401
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addBasicTokenPropertiesIfApplicable
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.getAccessToken
import org.nypl.simplified.accounts.api.AccountReadableType
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.BorrowSubtaskCredentials
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.httpConnectionFailed
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.httpRequestFailed
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowRecoverableAuthenticationError
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskFailed
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskFactoryType
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.links.Link
import java.io.ByteArrayInputStream

/**
 * A task that negotiates a Simplified bearer token. When the token is negotiated, the link within
 * the token, and the actual token value, are pushed into the borrow context in order to be used
 * by the next subtask.
 */

class BorrowBearerToken : BorrowSubtaskType {

  companion object : BorrowSubtaskFactoryType {
    private val objectMappers =
      LSSimplifiedBearerTokenObjectMappers.createObjectMapper()

    fun objectMapper(): ObjectMapper {
      return this.objectMappers
    }

    override val name: String
      get() = "Bearer Token Negotiation"

    override fun createSubtask(): BorrowSubtaskType {
      return BorrowBearerToken()
    }

    override fun isApplicableFor(
      type: MIMEType,
      target: Link?,
      account: AccountReadableType?,
      remaining: List<MIMEType>
    ): Boolean {
      return MIMECompatibility.isCompatibleStrictWithoutAttributes(
        type,
        StandardFormatNames.simplifiedBearerToken
      )
    }
  }

  override fun execute(context: BorrowContextType) {
    context.taskRecorder.beginNewStep("Handling bearer token negotiation...")
    context.bookDownloadIsRunning("Requesting download...", receivedSize = 0L)

    return try {
      val currentURI =
        context.currentLinkCheck()

      when (currentURI) {
        is Link.LinkBasic -> executeLinkBasic(context, currentURI)
        is Link.LinkTemplated -> {
          context.taskRecorder.currentStepFailed(
            message = "Cannot handle templated links for bearer tokens.",
            errorCode = "error-templated",
            extraMessages = listOf()
          )
          context.bookDownloadFailed()
        }
      }
    } catch (e: BorrowSubtaskFailed) {
      context.bookDownloadFailed()
      throw e
    }
  }

  private fun executeLinkBasic(
    context: BorrowContextType,
    currentURI: Link.LinkBasic
  ) {
    val credentials =
      context.account.loginState.credentials
    val auth =
      AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials)
    val request =
      context.httpClient.newRequest(currentURI.href)
        .setMethod(LSHTTPRequestBuilderType.Method.Get)
        .setAuthorization(auth)
        .addBasicTokenPropertiesIfApplicable(credentials)
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          context.account.updateBasicTokenCredentials(status.getAccessToken())
          this.handleOKRequest(context, status)
        }

        is LSHTTPResponseStatus.Responded.Error -> {
          this.handleHTTPError(context, status)
        }

        is LSHTTPResponseStatus.Failed -> {
          this.handleHTTPFailure(context, status)
        }
      }
    }
  }

  private fun handleOKRequest(
    context: BorrowContextType,
    status: LSHTTPResponseStatus.Responded.OK
  ) {
    val mapper =
      objectMapper()
    val inputStream =
      status.bodyStream ?: ByteArrayInputStream(ByteArray(0))
    val token =
      mapper.readValue(inputStream, LSSimplifiedBearerToken::class.java)

    context.setNextSubtaskCredentials(BorrowSubtaskCredentials.UseBearerToken(token.accessToken))
    context.receivedNewURI(Link.LinkBasic(token.location))
  }

  private fun handleHTTPFailure(
    context: BorrowContextType,
    status: LSHTTPResponseStatus.Failed
  ) {
    context.taskRecorder.currentStepFailed(
      message = status.exception.message ?: "Exception raised during connection attempt.",
      errorCode = httpConnectionFailed,
      exception = status.exception,
      extraMessages = listOf()
    )
    throw BorrowSubtaskFailed()
  }

  private fun handleHTTPError(
    context: BorrowContextType,
    status: LSHTTPResponseStatus.Responded.Error
  ) {
    val report = status.properties.problemReport
    if (report != null) {
      context.taskRecorder.addAttributes(report.toMap())
    }

    context.taskRecorder.currentStepFailed(
      message = "HTTP request failed: ${status.properties.originalStatus} ${status.properties.message}",
      errorCode = httpRequestFailed,
      exception = null,
      extraMessages = listOf()
    )

    throw if (status.properties.status == 401) {
      when (AccountAuthenticatedHTTP.handle401Error(status.properties.problemReport)) {
        Handled401.ErrorIsRecoverableCredentialsExpired -> {
          context.bookDownloadFailedBadCredentials()
          BorrowRecoverableAuthenticationError()
        }
        Handled401.ErrorIsUnrecoverable -> {
          BorrowSubtaskFailed()
        }
      }
    } else {
      BorrowSubtaskFailed()
    }
  }
}
