package org.librarysimplified.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.librarysimplified.services.api.ServiceDirectoryType
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory

class CatalogBookDetailViewModelFactory(
  private val services: ServiceDirectoryType,
  private val borrowViewModel: CatalogBorrowViewModel,
  private val listener: FragmentListenerType<CatalogBookDetailEvent>,
  private val parameters: CatalogBookDetailFragmentParameters
) : ViewModelProvider.NewInstanceFactory() {

  private val logger =
    LoggerFactory.getLogger(CatalogBookDetailViewModelFactory::class.java)

  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    this.logger.debug("requested creation of view model of type {}", modelClass)

    return when {
      modelClass.isAssignableFrom(CatalogBookDetailViewModel::class.java) -> {
        val feedLoader: FeedLoaderType =
          this.services.requireService(FeedLoaderType::class.java)
        val profilesController =
          services.requireService(ProfilesControllerType::class.java)
        val bookRegistry =
          services.requireService(BookRegistryType::class.java)
        val configurationService =
          services.requireService(BuildConfigurationServiceType::class.java)

        CatalogBookDetailViewModel(
          feedLoader,
          profilesController,
          bookRegistry,
          configurationService,
          this.borrowViewModel,
          parameters,
          listener
        ) as T
      }
      else ->
        super.create(modelClass)
    }
  }
}
