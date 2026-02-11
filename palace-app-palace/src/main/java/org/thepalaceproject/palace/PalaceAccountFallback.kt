package org.thepalaceproject.palace

import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountProviderFallbackType
import org.nypl.simplified.accounts.api.AccountProviderType
import java.net.URI
import java.time.OffsetDateTime

/**
 * The fallback account for Palace Bookshelf.
 */

class PalaceAccountFallback : AccountProviderFallbackType {

  override fun get(): AccountProviderType {
    return AccountProvider(
      authenticationDocumentURI = URI.create("https://dpla.thepalaceproject.org/bookshelf/authentication_document"),
      authentication = AccountProviderAuthenticationDescription.Anonymous,
      authenticationAlternatives = listOf(),
      cardCreatorURI = null,
      catalogURI = URI.create("https://dpla.thepalaceproject.org/bookshelf/"),
      description = "Popular books free to download and keep, handpicked by librarians across the US.",
      displayName = "Palace Bookshelf",
      eula = null,
      id = URI.create("urn:uuid:6b849570-070f-43b4-9dcc-7ebb4bca292e"),
      license = null,
      loansURI = URI.create("https://dpla.thepalaceproject.org/bookshelf/loans/"),
      logo = URI.create("https://tpp-prod-library-registry-public.s3.amazonaws.com/logo/6b849570-070f-43b4-9dcc-7ebb4bca292e.png"),
      mainColor = "blue",
      patronSettingsURI = URI.create("https://dpla.thepalaceproject.org/bookshelf/patrons/me/"),
      privacyPolicy = null,
      resetPasswordURI = null,
      subtitle = "Popular books free to download and keep, handpicked by librarians across the US.",
      supportEmail = "support@thepalaceproject.org",
      supportsReservations = false,
      updated = OffsetDateTime.parse("2021-07-07T23:10:18.238-04:00"),
      alternateURI = URI.create("https://thepalaceproject.org")
    )
  }
}
