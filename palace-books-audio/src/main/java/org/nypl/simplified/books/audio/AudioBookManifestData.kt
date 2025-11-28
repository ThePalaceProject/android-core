package org.nypl.simplified.books.audio

import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled

/**
 * A downloaded, parsed, and license checked manifest.
 */

data class AudioBookManifestData(

  /**
   * The manifest.
   */

  val manifest: PlayerManifest,

  /**
   * The bytes of the audiobook license, if present.
   */

  val licenseBytes: ByteArray?,

  /**
   * The original fulfilled manifest, suitable for writing to external storage.
   */

  val fulfilled: ManifestFulfilled
)
