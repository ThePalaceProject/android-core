package org.nypl.simplified.main

import android.content.Context
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import org.librarysimplified.services.api.ServiceDirectoryType
import org.nypl.drm.core.AdobeAdeptExecutorType
import org.nypl.simplified.adobe.extensions.AdobeDRMServices

/**
 * Functions to display Adobe DRM related warnings.
 */

object MainAdobeWarnings {

  @Volatile
  private var hasDisplayed: Boolean = false

  @UiThread
  fun showWarningDialogIfNecessary(
    context: Context,
    services: ServiceDirectoryType
  ) {
    try {
      if (!this.hasDisplayed) {
        val drmService = services.optionalService(AdobeAdeptExecutorType::class.java)
        if (drmService == null) {
          if (AdobeDRMServices.isIntendedToBePresent(context)) {
            AlertDialog.Builder(context)
              .setMessage(R.string.bootAdobeDRMFailed)
              .create()
              .show()
          }
        }
      }
    } finally {
      this.hasDisplayed = true
    }
  }
}
