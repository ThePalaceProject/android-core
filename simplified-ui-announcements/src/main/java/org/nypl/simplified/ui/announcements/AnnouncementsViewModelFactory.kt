package org.nypl.simplified.ui.announcements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.librarysimplified.services.api.ServiceDirectoryType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType

class AnnouncementsViewModelFactory(
  private val services: ServiceDirectoryType
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel?> create(modelClass: Class<T>): T & Any {
    return when {
      modelClass.isAssignableFrom(AnnouncementsViewModel::class.java) -> {
        val profilesController: ProfilesControllerType =
          this.services.requireService(ProfilesControllerType::class.java)

        AnnouncementsViewModel(profilesController) as (T & Any)
      }
      else ->
        throw IllegalArgumentException(
          "This view model factory (${this.javaClass}) cannot produce view models of type $modelClass"
        )
    }
  }
}
