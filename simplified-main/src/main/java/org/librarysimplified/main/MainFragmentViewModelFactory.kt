package org.librarysimplified.main

import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.slf4j.LoggerFactory

/**
 * A view model factory that can produce view models for each of the sections of the application
 * that view feeds.
 */

class MainFragmentViewModelFactory(
  private val resources: Resources
) : ViewModelProvider.Factory {

  private val logger =
    LoggerFactory.getLogger(MainFragmentViewModelFactory::class.java)

  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    this.logger.debug("requested creation of view model of type {}", modelClass)

    return MainFragmentViewModel(resources) as T
  }
}
