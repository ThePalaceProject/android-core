package org.librarysimplified.ui.catalog

import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.book_registry.BookStatus

data class CatalogBookStatus<S : BookStatus>(
  val book: Book,
  val status: S,
  val previewStatus: BookPreviewStatus,
)
