package org.librarysimplified.main

import android.content.Context
import androidx.annotation.UiThread
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
            MaterialAlertDialogBuilder(context)
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
