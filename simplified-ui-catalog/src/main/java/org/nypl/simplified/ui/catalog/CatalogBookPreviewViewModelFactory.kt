package org.nypl.simplified.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.librarysimplified.services.api.ServiceDirectoryType
import org.nypl.simplified.books.controller.api.BooksPreviewControllerType
import org.slf4j.LoggerFactory

/**
 * A view model factory that can produce view models for book preview operations.
 */

class CatalogBookPreviewViewModelFactory(
  private val services: ServiceDirectoryType
) : ViewModelProvider.Factory {

  private val logger =
    LoggerFactory.getLogger(CatalogBookPreviewViewModelFactory::class.java)

  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    this.logger.debug("requested creation of view model of type {}", modelClass)

    return when {
      modelClass.isAssignableFrom(CatalogBookPreviewViewModel::class.java) -> {
        val booksPreviewController: BooksPreviewControllerType =
          this.services.requireService(BooksPreviewControllerType::class.java)
        CatalogBookPreviewViewModel(booksPreviewController) as T
      }
      else ->
        throw IllegalArgumentException(
          "This view model factory (${this.javaClass}) cannot produce view models of type $modelClass"
        )
    }
  }
}
