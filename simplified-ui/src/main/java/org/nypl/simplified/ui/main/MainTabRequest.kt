package org.nypl.simplified.ui.main

sealed class MainTabRequest {

  data object TabAny : MainTabRequest()

  data class TabForCategory(
    val category: MainTabCategory
  ) : MainTabRequest()
}
