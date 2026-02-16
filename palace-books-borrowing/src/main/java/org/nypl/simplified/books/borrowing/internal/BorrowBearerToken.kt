package org.nypl.simplified.books.borrowing.internal

import one.irradia.mime.api.MIMECompatibility
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.bearer_token.LSSimplifiedBearerTokenNegotiation
import org.librarysimplified.http.refresh_token.LSHTTPRefreshTokenProperties
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.Handled401
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountReadableType
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.BorrowSubtaskCredentials
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.httpRequestFailed
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowRecoverableAuthenticationError
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskFailed
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskFactoryType
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.links.Link

/**
 * A task that negotiates a Simplified bearer token. When the token is negotiated, the link within
 * the token, and the actual token value, are pushed into the borrow context in order to be used
 * by the next subtask.
 */

class BorrowBearerToken : BorrowSubtaskType {

  companion object : BorrowSubtaskFactoryType {
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
      when (val currentURI = context.currentLinkCheck()) {
        is Link.LinkBasic -> this.executeLinkBasic(context, currentURI)
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

    val refreshTokenProperties =
      when (credentials) {
        is AccountAuthenticationCredentials.BasicToken -> {
          LSHTTPRefreshTokenProperties(
            userName = credentials.userName.value,
            password = credentials.password.value,
            refreshURI = credentials.authenticationTokenInfo.authURI
          )
        }

        is AccountAuthenticationCredentials.Basic -> null
        is AccountAuthenticationCredentials.OAuthWithIntermediary -> null
        is AccountAuthenticationCredentials.SAML2_0 -> null
        null -> null
      }

    val tokenResult =
      LSSimplifiedBearerTokenNegotiation.negotiate(
        context.httpClient,
        target = currentURI.href,
        refreshTokenProperties = refreshTokenProperties,
        authorization = auth
      )

    return when (tokenResult) {
      is LSSimplifiedBearerTokenNegotiation.NegotiationFailed -> {
        this.handleNegotiationFailure(context, currentURI, tokenResult)
      }

      is LSSimplifiedBearerTokenNegotiation.NegotiationSucceeded -> {
        this.handleNegotiationSuccess(context, currentURI, tokenResult)
      }
    }
  }

  private fun handleNegotiationFailure(
    context: BorrowContextType,
    currentURI: Link.LinkBasic,
    tokenResult: LSSimplifiedBearerTokenNegotiation.NegotiationFailed
  ) {
    val properties = tokenResult.response?.properties
    val statusCode = properties?.status ?: 600
    val report = properties?.problemReport
    if (report != null) {
      context.taskRecorder.addAttributes(report.toMap())
    }
    val message = properties?.message

    context.taskRecorder.currentStepFailed(
      message = "HTTP request failed: ${currentURI.href} $statusCode $message",
      errorCode = httpRequestFailed,
      exception = null,
      extraMessages = listOf()
    )

    throw if (statusCode == 401) {
      when (AccountAuthenticatedHTTP.handle401Error(report)) {
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

  private fun handleNegotiationSuccess(
    context: BorrowContextType,
    currentURI: Link.LinkBasic,
    tokenResult: LSSimplifiedBearerTokenNegotiation.NegotiationSucceeded
  ) {
    val refreshToken = tokenResult.refreshToken
    if (refreshToken != null) {
      context.account.updateBasicTokenCredentials(refreshToken.value)
    }
    val bearerToken = tokenResult.token
    context.setNextSubtaskCredentials(
      BorrowSubtaskCredentials.UseBearerToken(
        refreshURI = currentURI.href,
        token = bearerToken.accessToken
      )
    )
    context.receivedNewURI(Link.LinkBasic(bearerToken.location))
  }
}
