package org.nypl.simplified.accounts.source.nyplregistry

import android.content.Context
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollection
import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollectionParsersType
import org.nypl.simplified.accounts.api.AccountProviderResolutionListenerType
import org.nypl.simplified.accounts.api.AccountProviderResolutionStringsType
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryDebugging
import org.nypl.simplified.accounts.source.nyplregistry.AccountProviderSourceNYPLRegistryException.ServerConnectionFailure
import org.nypl.simplified.accounts.source.nyplregistry.AccountProviderSourceNYPLRegistryException.ServerReturnedError
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceResolutionStrings
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceType
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceType.SourceResult
import org.nypl.simplified.opds.auth_document.api.AuthenticationDocumentParsersType
import org.nypl.simplified.parser.api.ParseResult
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI

/**
 * A server-based account provider.
 */

class AccountProviderSourceNYPLRegistry(
  private val http: LSHTTPClientType,
  private val authDocumentParsers: AuthenticationDocumentParsersType,
  private val parsers: AccountProviderDescriptionCollectionParsersType,
  private val uriProduction: URI,
  private val uriQA: URI
) : AccountProviderSourceType {

  private val logger =
    LoggerFactory.getLogger(AccountProviderSourceNYPLRegistry::class.java)

  @Volatile
  private var stringResources: AccountProviderResolutionStringsType? = null

  override fun load(
    context: Context,
    includeTestingLibraries: Boolean
  ): SourceResult {
    if (this.stringResources == null) {
      this.stringResources =
        AccountProviderSourceResolutionStrings(
          context.resources
        )
    }

    return try {
      SourceResult.SourceSucceeded(fetchServerResults(includeTestingLibraries))
    } catch (e: Exception) {
      this.logger.debug("failed to fetch providers: ", e)
      SourceResult.SourceFailed(mapOf(), e)
    }
  }

  override fun canResolve(description: AccountProviderDescription): Boolean {
    /*
     * We assume that the NYPL registry can always resolve any account description.
     */

    return true
  }

  override fun resolve(
    onProgress: AccountProviderResolutionListenerType,
    description: AccountProviderDescription
  ): TaskResult<AccountProviderType> {
    return AccountProviderResolution(
      stringResources = this.stringResources!!,
      authDocumentParsers = this.authDocumentParsers,
      http = this.http,
      description = description
    ).resolve(onProgress)
  }

  /**
   * Decide which server URI to use based on debugging properties and whether or not to include
   * testing libraries.
   */

  private fun decideRegistryURI(
    includeTestingLibraries: Boolean
  ): URI {
    val debuggingBase =
      AccountProviderRegistryDebugging.properties[
        "org.nypl.simplified.accounts.source.nyplregistry.baseServerOverride"
      ]
    return if (debuggingBase != null) {
      when (includeTestingLibraries) {
        true -> URI.create("https://$debuggingBase/libraries/qa")
        false -> URI.create("https://$debuggingBase/libraries")
      }
    } else {
      when (includeTestingLibraries) {
        true -> this.uriQA
        false -> this.uriProduction
      }
    }
  }

  /**
   * Fetch a set of provider descriptions from the server.
   */

  private fun fetchServerResults(
    includeTestingLibraries: Boolean
  ): Map<URI, AccountProviderDescription> {
    val targetURI = this.decideRegistryURI(includeTestingLibraries)
    this.logger.debug("fetching QA providers from $targetURI")
    val results =
      this.fetchAndParse(targetURI)
        .providers
        .associateBy { it.id }

    this.logger.debug("categorizing ${results.size} providers")
    return results
  }

  private fun fetchAndParse(target: URI): AccountProviderDescriptionCollection {
    return this.openStream(target).use { stream ->
      this.parseFromStream(target, stream)
    }
  }

  private fun parseFromStream(
    target: URI,
    stream: InputStream
  ): AccountProviderDescriptionCollection {
    return this.parsers.createParser(target, stream, warningsAsErrors = false).use { parser ->
      when (val parseResult = parser.parse()) {
        is ParseResult.Success ->
          parseResult.result

        is ParseResult.Failure -> {
          this.logParseFailure("server", parseResult)

          throw AccountProviderSourceNYPLRegistryException.ServerReturnedUnparseableData(
            uri = target,
            warnings = parseResult.warnings,
            errors = parseResult.errors
          )
        }
      }
    }
  }

  private fun logParseFailure(
    source: String,
    parseResult: ParseResult.Failure<AccountProviderDescriptionCollection>
  ) {
    this.logger.debug(
      "failed to parse providers from $source ({} errors, {} warnings)",
      parseResult.errors.size,
      parseResult.warnings.size
    )

    parseResult.errors.forEach { this.logger.error("parse error: {}: ", it.message) }
    parseResult.warnings.forEach { this.logger.warn("parse warning: {}: ", it.message) }
  }

  private fun openStream(target: URI): InputStream {
    val request =
      this.http.newRequest(target)
        .build()

    val response = request.execute()
    return when (val status = response.status) {
      is LSHTTPResponseStatus.Responded.OK ->
        status.bodyStream ?: ByteArrayInputStream(ByteArray(0))

      is LSHTTPResponseStatus.Responded.Error ->
        throw ServerReturnedError(
          uri = target,
          errorCode = status.properties.status,
          message = status.properties.message,
          problemReport = status.properties.problemReport
        )

      is LSHTTPResponseStatus.Failed ->
        throw ServerConnectionFailure(
          uri = target,
          cause = status.exception
        )
    }
  }
}
