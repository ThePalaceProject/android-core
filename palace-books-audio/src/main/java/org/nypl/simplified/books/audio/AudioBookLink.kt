package org.nypl.simplified.books.audio

import java.net.URI

sealed class AudioBookLink {

  abstract val target: URI

  data class Manifest(
    override val target: URI
  ) : AudioBookLink()

  data class License(
    override val target: URI
  ) : AudioBookLink()
}
