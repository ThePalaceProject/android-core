package org.nypl.simplified.books.book_registry

import io.reactivex.Observable
import java.io.File

interface BookPreviewRegistryType {

  fun observeBookPreviewStatus(): Observable<BookPreviewStatus>

  fun updatePreviewStatus(status: BookPreviewStatus)

  fun getPreviewDownloadDirectory(): File
}
