package org.nypl.simplified.accounts.json

import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollectionParserType
import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollectionParsersType
import org.thepalaceproject.webpub.core.WPMManifest
import org.thepalaceproject.webpub.core.WPMMappers
import java.io.InputStream
import java.net.URI

/**
 * A provider of account description collection parsers.
 */

class AccountProviderDescriptionCollectionParsers : AccountProviderDescriptionCollectionParsersType {
  private val wpmMapper =
    WPMMappers.createMapper()

  override fun createParser(
    uri: URI,
    stream: InputStream,
    warningsAsErrors: Boolean
  ): AccountProviderDescriptionCollectionParserType =
    AccountProviderDescriptionCollectionParser(uri) {
      this.wpmMapper.readValue(stream, WPMManifest::class.java)
    }
}
