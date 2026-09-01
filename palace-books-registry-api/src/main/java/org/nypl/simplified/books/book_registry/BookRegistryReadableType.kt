package org.nypl.simplified.books.book_registry

import io.reactivex.Observable
import org.nypl.simplified.books.api.BookID
import java.util.Optional
import java.util.SortedMap

/**
 * The type of readable book registries.
 */

interface BookRegistryReadableType {
  /**
   * @return A read-only map of the known books
   */

  fun books(): SortedMap<BookID, BookWithStatus>

  /**
   * @return An observable that publishes book status events
   */

  fun bookEvents(): Observable<BookStatusEvent>

  /**
   * @return An observable that publishes book holds events
   */

  fun bookHoldsUpdateEvents(): Observable<BookHoldsUpdateEvent>

  /**
   * @param id The book ID
   * @return The status for the given book, if any.
   */

  fun bookStatus(id: BookID): Optional<BookStatus>

  /**
   * @param id The book ID
   * @return The status for the given book, if any.
   */

  fun bookStatusOrNull(id: BookID): BookStatus? {
    return this.bookStatus(id).orElse(null)
  }

  /**
   * @param id The book ID
   * @return The registered book, if any
   */

  fun book(id: BookID): Optional<BookWithStatus>

  /**
   * @param id The book ID
   * @return The registered book, if any
   */

  fun bookOrNull(id: BookID): BookWithStatus? {
    return this.book(id).orElse(null)
  }

  /**
   * @param id The book ID
   * @return The registered book
   * @throws NoSuchElementException If the given book does not exist
   */

  @Throws(NoSuchElementException::class)
  fun bookOrException(id: BookID): BookWithStatus =
    book(id).orElseThrow {
      NoSuchElementException("No such book: " + id.value())
    }
}
