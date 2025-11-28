package org.nypl.simplified.ui.catalog

import io.reactivex.Observable
import org.nypl.simplified.books.book_registry.BookRegistryReadableType
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatusEvent
import org.nypl.simplified.ui.events.UISubjectRelay

/**
 * A silly UI abstraction that relays events on the UI thread. This
 * is required because apparently [Observable.observeOn] cannot be trusted.
 */

class CatalogBookRegistryEvents private constructor(
  val registry: BookRegistryReadableType,
  private val relay: UISubjectRelay<BookStatusEvent>
) {
  companion object {
    fun create(
      bookRegistry: BookRegistryType
    ): CatalogBookRegistryEvents {
      return CatalogBookRegistryEvents(
        bookRegistry,
        UISubjectRelay.create(bookRegistry.bookEvents())
      )
    }
  }

  /**
   * A stream of book status events that are guaranteed to be observed on the UI thread.
   */

  val events: Observable<BookStatusEvent> =
    this.relay.events
}
