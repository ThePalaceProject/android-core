package org.nypl.simplified.accounts.json

import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollection
import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollectionParserType
import org.nypl.simplified.links.Links
import org.nypl.simplified.parser.api.ParseError
import org.nypl.simplified.parser.api.ParseResult
import org.nypl.simplified.parser.api.ParseWarning
import org.slf4j.LoggerFactory
import org.thepalaceproject.webpub.core.WPMCatalog
import org.thepalaceproject.webpub.core.WPMManifest
import org.thepalaceproject.webpub.core.WPMMetadata
import java.net.URI

/**
 * A parser of provider description collections.
 */

class AccountProviderDescriptionCollectionParser internal constructor(
  private val source: URI,
  private val manifestSupplier: () -> WPMManifest
) : AccountProviderDescriptionCollectionParserType {
  private val logger =
    LoggerFactory.getLogger(AccountProviderDescriptionCollectionParser::class.java)

  private val errors = mutableListOf<ParseError>()
  private val warnings = mutableListOf<ParseWarning>()

  private fun logError(message: String) {
    this.errors.add(
      ParseError(
        source = this.source,
        message = message,
        line = 0,
        column = 0,
        exception = null
      )
    )
  }

  override fun parse(): ParseResult<AccountProviderDescriptionCollection> {
    return try {
      val manifest = this.manifestSupplier.invoke()
      if (manifest.catalogs.isEmpty()) {
        this.logError("No catalogs were provided in the given feed.")
        return ParseResult.Failure(
          warnings = this.warnings.toList(),
          errors = this.errors.toList()
        )
      } else {
        this.processCatalogs(manifest)
      }
    } catch (e: Exception) {
      this.errors.add(
        ParseError(
          source = this.source,
          message = e.message ?: e.javaClass.name,
          line = 0,
          column = 0,
          exception = e
        )
      )
      ParseResult.Failure(
        warnings = this.warnings.toList(),
        errors = this.errors.toList()
      )
    }
  }

  private fun processCatalogs(manifest: WPMManifest): ParseResult<AccountProviderDescriptionCollection> {
    val metadata =
      this.processMetadata(manifest.metadata)
    val accountDescriptions =
      manifest.catalogs.mapNotNull(this::processCatalog)

    if (this.errors.isEmpty()) {
      return ParseResult.Success(
        warnings = this.warnings.toList(),
        result =
          AccountProviderDescriptionCollection(
            providers = accountDescriptions,
            links = manifest.links.map { link -> Links.wpmLinkToPalaceLink(link) },
            metadata = metadata
          )
      )
    }

    return ParseResult.Failure(
      warnings = this.warnings.toList(),
      errors = this.errors.toList()
    )
  }

  private fun processCatalog(catalog: WPMCatalog): AccountProviderDescription? {
    val errorsThen = this.errors.size

    val id = catalog.metadata.identifier
    if (id == null) {
      this.logError("An identifier is required for catalog ${catalog.metadata.title.defaultValue}")
    }

    val updated = catalog.metadata.modified ?: catalog.metadata.updated
    if (updated == null) {
      this.logError("An 'updated' time is required for catalog ${catalog.metadata.title.defaultValue}")
    }

    val errorsNow = this.errors.size
    if (errorsNow != errorsThen) {
      return null
    }

    return AccountProviderDescription(
      id = id!!,
      title = catalog.metadata.title.defaultValue,
      description = catalog.metadata.description,
      updated = updated!!,
      links = catalog.links.map { link -> Links.wpmLinkToPalaceLink(link) },
      images = catalog.images.map { link -> Links.wpmLinkToPalaceLink(link) },
    )
  }

  private fun processMetadata(metadata: WPMMetadata): AccountProviderDescriptionCollection.Metadata =
    AccountProviderDescriptionCollection.Metadata(
      title = metadata.title.defaultValue
    )

  override fun close() {
    // Nothing required.
  }
}
