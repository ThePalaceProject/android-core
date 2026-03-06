package org.nypl.simplified.profiles.api

import java.io.File

/**
 * The interface exposed by the profiles database.
 */

interface ProfilesDatabaseType {

  /**
   * Access the profile.
   *
   * @return The anonymous profile
   */

  fun anonymousProfile(): ProfileType = this.currentProfile()

  /**
   * @return The directory containing the on-disk profiles database
   */

  fun directory(): File

  /**
   * @return The current profile
   */

  fun currentProfile(): ProfileType
}
