package org.nypl.simplified.ui.settings

import com.google.common.util.concurrent.MoreExecutors
import com.io7m.jattribute.core.AttributeType
import org.librarysimplified.services.api.Services
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.main.MainAttributes
import java.net.URI

object SettingsCustomOPDSModel {

  val taskRunning: AttributeType<Boolean> =
    MainAttributes.attributes.withValue(false)

  fun createCustomOPDSFeed(
    uri: String
  ) {
    this.taskRunning.set(true)

    val future =
      Services.serviceDirectory()
        .requireService(ProfilesControllerType::class.java)
        .profileAccountCreateCustomOPDS(URI(uri))

    future.addListener(
      { this.taskRunning.set(false) },
      MoreExecutors.directExecutor()
    )
  }
}
