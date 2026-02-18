package org.nypl.simplified.patron

import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addBasicTokenPropertiesIfApplicable
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.getAccessToken
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.parser.api.ParseError
import org.nypl.simplified.parser.api.ParseResult
import org.nypl.simplified.parser.api.ParseWarning
import org.nypl.simplified.patron.api.PatronUserProfile
import org.nypl.simplified.patron.api.PatronUserProfileParsersType
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

object PatronUserProfiles {

  private val logger = LoggerFactory.getLogger(PatronUserProfiles::class.java)

  /**
   * Execute a patron profile document request. This fetches patron settings from the remote
   * server and attempts to extract useful information such as DRM-related credentials.
   */

  fun runPatronProfileRequest(
    taskRecorder: TaskRecorderType,
    patronParsers: PatronUserProfileParsersType,
    credentials: AccountAuthenticationCredentials,
    http: LSHTTPClientType,
    account: AccountType
  ): PatronUserProfile {
    val patronSettingsURI = account.provider.patronSettingsURI
    if (patronSettingsURI == null) {
      val exception = Exception()
      taskRecorder.currentStepFailed(
        message = "No available patron user profile URI",
        errorCode = "noPatronURI",
        exception = exception,
        extraMessages = listOf()
      )
      throw exception
    }

    val request =
      http.newRequest(patronSettingsURI)
        .setAuthorization(AccountAuthenticatedHTTP.createAuthorization(credentials))
        .addBasicTokenPropertiesIfApplicable(credentials)
        .build()

    val response = request.execute()
    return when (val status = response.status) {
      is LSHTTPResponseStatus.Responded.OK -> {
        account.updateBasicTokenCredentials(status.getAccessToken())
        this.onPatronProfileRequestOK(
          taskRecorder = taskRecorder,
          patronSettingsURI = patronSettingsURI,
          patronParsers = patronParsers,
          stream = status.bodyStream ?: ByteArrayInputStream(ByteArray(0))
        )
      }

      is LSHTTPResponseStatus.Responded.Error -> {
        this.onPatronProfileRequestHTTPError(
          taskRecorder = taskRecorder,
          patronSettingsURI = patronSettingsURI,
          result = status
        )
      }

      is LSHTTPResponseStatus.Failed -> {
        this.onPatronProfileRequestHTTPException(
          taskRecorder = taskRecorder,
          patronSettingsURI = patronSettingsURI,
          result = status
        )
      }
    }
  }

  /**
   * A patron settings document was received. Parse it and try to extract any required
   * DRM information.
   */

  private fun onPatronProfileRequestOK(
    taskRecorder: TaskRecorderType,
    patronSettingsURI: URI,
    patronParsers: PatronUserProfileParsersType,
    stream: InputStream
  ): PatronUserProfile {
    return patronParsers.createParser(patronSettingsURI, stream).use { parser ->
      when (val parseResult = parser.parse()) {
        is ParseResult.Success -> {
          this.logger.debug("parsed patron profile successfully")
          parseResult.warnings.forEach(this::logParseWarning)
          taskRecorder.currentStepSucceeded("Parsed patron user profile")
          parseResult.result
        }

        is ParseResult.Failure -> {
          this.logger.error("failed to parse patron profile")
          val message: String =
            parseResult.errors.map(this::showParseError)
              .joinToString("\n")
          val exception = Exception()
          taskRecorder.currentStepFailed(
            message = message,
            errorCode = "parseErrorPatronSettings",
            exception = exception,
            extraMessages = listOf()
          )
          throw exception
        }
      }
    }
  }

  /**
   * Log and convert a parse error to a humanly-readable string.
   */

  private fun showParseError(error: ParseError): String {
    this.logger.error(
      "{}:{}:{}: {}: ",
      error.source,
      error.line,
      error.column,
      error.message,
      error.exception
    )

    return buildString {
      this.append(error.line)
      this.append(':')
      this.append(error.column)
      this.append(": ")
      this.append(error.message)
      val ex = error.exception
      if (ex != null) {
        this.append(ex.message)
        this.append(" (")
        this.append(ex.javaClass.simpleName)
        this.append(")")
      }
    }
  }

  private fun logParseWarning(warning: ParseWarning) {
    this.logger.warn(
      "{}:{}:{}: {}: ",
      warning.source,
      warning.line,
      warning.column,
      warning.message,
      warning.exception
    )
  }

  @Suppress("UNUSED_PARAMETER")
  private fun <T> onPatronProfileRequestHTTPException(
    taskRecorder: TaskRecorderType,
    patronSettingsURI: URI,
    result: LSHTTPResponseStatus.Failed
  ): T {
    taskRecorder.currentStepFailed(
      message = "Connection failed when fetching patron user profile.",
      errorCode = "connectionFailed",
      exception = result.exception,
      extraMessages = listOf()
    )
    throw result.exception
  }

  private fun <T> onPatronProfileRequestHTTPError(
    taskRecorder: TaskRecorderType,
    patronSettingsURI: URI,
    result: LSHTTPResponseStatus.Responded.Error
  ): T {
    this.logger.error(
      "received http error: {}: {}: {}",
      patronSettingsURI,
      result.properties.message,
      result.properties.status
    )

    val exception = Exception()
    when (result.properties.status) {
      HttpURLConnection.HTTP_UNAUTHORIZED -> {
        taskRecorder.currentStepFailed(
          message = "Invalid credentials!",
          errorCode = "invalidCredentials",
          exception = exception,
          extraMessages = listOf()
        )
        throw exception
      }

      else -> {
        taskRecorder.addAttributesIfPresent(result.properties.problemReport?.toMap())
        taskRecorder.currentStepFailed(
          message = "Server error: ${result.properties.status} ${result.properties.message}",
          errorCode = "httpError ${result.properties.status} $patronSettingsURI",
          exception = exception,
          extraMessages = listOf()
        )
        throw exception
      }
    }
  }
}
