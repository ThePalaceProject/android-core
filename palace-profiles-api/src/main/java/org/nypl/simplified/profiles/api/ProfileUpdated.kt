package org.nypl.simplified.profiles.api

/**
 * The type of profile preferences events.
 */

sealed class ProfileUpdated : ProfileEvent() {

  data class Succeeded(
    val oldDescription: ProfileDescription,
    val newDescription: ProfileDescription
  ) : ProfileUpdated()

  data class Failed(
    val exception: Exception
  ) : ProfileUpdated()
}
