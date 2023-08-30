package org.nypl.simplified.viewer.audiobook

import one.irradia.mime.vanilla.MIMEParser
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.audio.AudioBookCredentials
import org.nypl.simplified.books.audio.AudioBookManifestRequest
import org.nypl.simplified.books.audio.AudioBookManifestStrategiesType
import org.nypl.simplified.books.audio.AudioBookManifestStrategyType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import java.io.File
import java.io.Serializable
import java.net.URI

/**
 * Parameters for the audio book player.
 */

class AudioBookPlayerParameters(

  /**
   * The audio book file, if this is a packaged audio book. This must be null for unpackaged audio
   * books.
   */

  val file: File?,

  /**
   * The user agent string used to make manifest requests.
   */

  val userAgent: String,

  /**
   * The current manifest content type.
   */

  val manifestContentType: String,

  /**
   * The current manifest file.
   */

  val manifestFile: File,

  /**
   * A URI that can be used to fetch a more up-to-date copy of the manifest.
   */

  val manifestURI: URI,

  /**
   * The account to which the book belongs.
   */

  val accountID: AccountID,

  /**
   * The account provider to which the book belongs.
   */

  val accountProviderID: URI,

  /**
   * The book ID.
   */

  val bookID: BookID,

  /**
   * The OPDS entry for the book.
   */

  val opdsEntry: OPDSAcquisitionFeedEntry,

  /**
   * The DRM information for the book.
   */

  val drmInfo: BookDRMInformation
) : Serializable {

  /**
   * Create a manifest strategy for the current parameters.
   */

  fun toManifestStrategy(
    strategies: AudioBookManifestStrategiesType,
    isNetworkAvailable: () -> Boolean,
    credentials: AccountAuthenticationCredentials?,
    cacheDirectory: File
  ): AudioBookManifestStrategyType {
    val manifestContentType =
      MIMEParser.parseRaisingException(this.manifestContentType)
    val userAgent =
      PlayerUserAgent(this.userAgent)

    val audioBookCredentials =
      when (credentials) {
        is AccountAuthenticationCredentials.Basic -> {
          if (credentials.password.value.isBlank()) {
            AudioBookCredentials.UsernameOnly(
              userName = credentials.userName.value
            )
          } else {
            AudioBookCredentials.UsernamePassword(
              userName = credentials.userName.value,
              password = credentials.password.value
            )
          }
        }
        is AccountAuthenticationCredentials.BasicToken -> {
          if (credentials.password.value.isBlank()) {
            AudioBookCredentials.UsernameOnly(
              userName = credentials.userName.value
            )
          } else {
            AudioBookCredentials.UsernamePassword(
              userName = credentials.userName.value,
              password = credentials.password.value
            )
          }
        }
        is AccountAuthenticationCredentials.OAuthWithIntermediary -> {
          AudioBookCredentials.BearerToken(credentials.accessToken)
        }
        is AccountAuthenticationCredentials.SAML2_0 -> {
          AudioBookCredentials.BearerToken(credentials.accessToken)
        }
        null -> {
          null
        }
      }

    val request =
      AudioBookManifestRequest(
        file = this.file,
        targetURI = this.manifestURI,
        contentType = manifestContentType,
        userAgent = userAgent,
        credentials = audioBookCredentials,
        services = Services.serviceDirectory(),
        isNetworkAvailable = isNetworkAvailable,
        loadFallbackData = {
          ManifestFulfilled(manifestContentType, null, this.manifestFile.readBytes())
        },
        cacheDirectory = cacheDirectory
      )

    return strategies.createStrategy(request)
  }
}
