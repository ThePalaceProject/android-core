package org.nypl.simplified.books.book_registry

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.slf4j.LoggerFactory
import java.io.File

class BookPreviewRegistry(
  val downloadDirectory: File
) : BookPreviewRegistryType {

  private val logger =
    LoggerFactory.getLogger(BookPreviewRegistry::class.java)
  private val bookPreviewStatusSubject: PublishSubject<BookPreviewStatus> =
    PublishSubject.create()

  override fun observeBookPreviewStatus(): Observable<BookPreviewStatus> {
    return this.bookPreviewStatusSubject
  }

  override fun updatePreviewStatus(status: BookPreviewStatus) {
    this.logger.debug("new status received {}", status)
    bookPreviewStatusSubject.onNext(status)
  }

  override fun getPreviewDownloadDirectory(): File {
    return downloadDirectory
  }
}
