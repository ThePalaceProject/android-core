package org.librarysimplified.main

sealed class MainFragmentState {

  /**
   * Nothing to remember.
   */

  object EmptyState : MainFragmentState()

  /**
   * Catalog required the patron to be logged in.
   */

  object CatalogWaitingForLogin : MainFragmentState()

  /**
   * Book details required the patron to be logged in.
   */

  object BookDetailsWaitingForLogin : MainFragmentState()
}
