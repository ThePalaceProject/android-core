package org.nypl.simplified.books.preview

import org.joda.time.Instant
import org.librarysimplified.http.api.LSHTTPClientType
import java.io.File

data class BookPreviewRequirements(
  val clock: () -> Instant,
  val httpClient: LSHTTPClientType,
  val temporaryDirectory: File
)
