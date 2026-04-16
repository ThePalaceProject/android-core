package org.nypl.simplified.ui.main

import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.threads.UIThread
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * An observable stream of book open requests. This class exists to allow for opening books
 * from the background. This is required for, for example, Android Auto.
 */

object MainBackgroundBookOpenRequests {

  private val requestsSub: Disposable

  private val logger =
    LoggerFactory.getLogger(MainBackgroundBookOpenRequests::class.java)

  private val requests =
    PublishSubject.create<BookOpenRequest>()
      .toSerialized()

  private val requestsOnUI =
    PublishSubject.create<BookOpenRequest>()
      .toSerialized()

  init {
    this.requestsSub =
      this.requests.subscribe { id ->
        UIThread.runOnUIThread {
          try {
            this.requestsOnUI.onNext(id)
          } catch (e: Throwable) {
            this.logger.debug("Failed to handle book request: ", e)
          }
        }
      }
  }

  data class BookOpenRequest(
    val book: Book,
    val bookFormat: BookFormat,
    val playerID: UUID
  )

  /**
   * Request that a book be opened.
   */

  fun requestBookOpen(
    request: BookOpenRequest
  ) {
    this.logger.debug("Requesting background load of audio book: {}", request)
    this.requests.onNext(request)
  }

  /**
   * The request stream, published on the UI thread.
   */

  val requestStream: Observable<BookOpenRequest> =
    this.requestsOnUI
}
