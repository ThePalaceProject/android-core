package org.nypl.simplified.tests.books.audio

import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.manifest.api.PlayerManifestMetadata
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderItem
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsersType
import org.librarysimplified.audiobook.manifest_parser.api.ManifestUnparsed
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.parser.api.ParseResult
import java.net.URI

object AudioBookSucceedingParsers : ManifestParsersType {

  val playerManifest =
    PlayerManifest(
      originalBytes = ByteArray(23),
      readingOrder = listOf(
        PlayerManifestReadingOrderItem(
          PlayerManifestReadingOrderID("urn:0"),
          PlayerManifestLink.LinkBasic(URI.create("http://www.example.com"))
        )
      ),
      metadata = PlayerManifestMetadata(
        title = "A book",
        identifier = "c925eb26-ab0c-44e2-9bec-ca4c38c0b6c8",
        encrypted = null
      ),
      links = listOf(),
      extensions = listOf(),
      toc = listOf(),
      palaceId = PlayerPalaceID("c925eb26-ab0c-44e2-9bec-ca4c38c0b6c8")
    )

  override fun parse(
    uri: URI,
    input: ManifestUnparsed
  ): ParseResult<PlayerManifest> {
    return ParseResult.Success(
      warnings = listOf(),
      result = playerManifest
    )
  }

  override fun parse(
    uri: URI,
    input: ManifestUnparsed,
    extensions: List<ManifestParserExtensionType>
  ): ParseResult<PlayerManifest> {
    return parse(uri, input)
  }
}
