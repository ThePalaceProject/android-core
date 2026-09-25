package org.thepalaceproject.mdm

import android.app.Application
import android.content.RestrictionsManager
import org.nypl.simplified.profiles.api.ProfileType
import org.slf4j.LoggerFactory

object MDMConfiguration {
  private val logger =
    LoggerFactory.getLogger(MDMConfiguration::class.java)

  fun setup(
    application: Application,
    profile: ProfileType
  ) {
    this.logger.debug("Configuring...")

    val restrictionsManager =
      application.getSystemService(RestrictionsManager::class.java)
    val data =
      restrictionsManager.applicationRestrictions

    this.logger.debug("Data: {}", data)
  }
}
