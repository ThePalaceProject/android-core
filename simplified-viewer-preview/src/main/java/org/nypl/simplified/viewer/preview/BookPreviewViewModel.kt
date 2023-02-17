package org.nypl.simplified.viewer.preview

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import org.nypl.simplified.books.book_registry.BookPreviewRegistryType
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.controller.api.BooksPreviewControllerType
import org.nypl.simplified.feeds.api.FeedEntry
import org.slf4j.LoggerFactory

/**
 * A view model that handles the preview of a book.
 */

class BookPreviewViewModel(
  private val bookPreviewRegistry: BookPreviewRegistryType,
  private val booksPreviewController: BooksPreviewControllerType
) : ViewModel() {

  private val logger =
    LoggerFactory.getLogger(BookPreviewViewModel::class.java)

  private val subscriptions =
    CompositeDisposable(
      this.bookPreviewRegistry.observeBookPreviewStatus()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onBookPreviewStatus)
    )

  private val bookPreviewStatusMutable: MutableLiveData<BookPreviewStatus> =
    MutableLiveData()

  val previewStatusLive: LiveData<BookPreviewStatus>
    get() = bookPreviewStatusMutable

  override fun onCleared() {
    subscriptions.clear()
    super.onCleared()
  }

  private fun onBookPreviewStatus(status: BookPreviewStatus) {
    this.bookPreviewStatusMutable.value = status
  }

  fun handlePreviewStatus(entry: FeedEntry.FeedEntryOPDS) {
    this.logger.debug("handling preview status: {}", entry.bookID)

    booksPreviewController.handleBookPreviewStatus(entry)
  }
}
