package org.nypl.simplified.accounts.registry

import org.nypl.simplified.accounts.api.AccountProviderDescription
import java.net.URI

/**
 * Error codes raised during account resolution. Applications MUST NOT depend on these
 * error codes to implement program logic; they are solely exposed here to facilitate
 * unit testing.
 */

object AccountProviderResolutionErrorCodes {
  fun unexpectedException(description: AccountProviderDescription): String = "unexpectedException ${description.id} ${description.title}"

  fun authDocumentUnusable(description: AccountProviderDescription): String = "authDocumentUnusable ${description.id} ${description.title}"

  fun authDocumentUnusableLink(description: AccountProviderDescription): String =
    "authDocumentUnusableLink ${description.id} ${description.title}"

  fun authDocumentParseFailed(description: AccountProviderDescription): String =
    "authDocumentParseFailed ${description.id} ${description.title}"

  fun httpRequestFailed(
    uri: URI?,
    status: Int,
    message: String
  ): String = "httpRequestFailed $uri $status $message"
}
