package org.nypl.simplified.books.book_database

import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import java.util.concurrent.CompletableFuture

/**
 * A download provider that does nothing.
 */

internal class NullDownloadProvider : PlayerDownloadProviderType {
  override fun download(request: PlayerDownloadRequest): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    future.completeExceptionally(UnsupportedOperationException())
    return future
  }
}
