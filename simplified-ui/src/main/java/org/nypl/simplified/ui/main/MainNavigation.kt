package org.nypl.simplified.ui.main

import org.nypl.simplified.ui.accounts.AccountCardCreatorModel
import org.nypl.simplified.ui.accounts.AccountCardCreatorParameters
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.settings.SettingsDocumentViewerModel
import java.net.URL

object MainNavigation {

  fun openErrorPage(
    parameters: ErrorPageParameters
  ) {
    TODO()
  }

  object Settings {
    fun openCardCreator(parameters: AccountCardCreatorParameters) {
      AccountCardCreatorModel.parameters = parameters
      TODO()
    }

    fun openDocument(
      title: String,
      documentURL: URL
    ) {
      SettingsDocumentViewerModel.target =
        SettingsDocumentViewerModel.Target(
          title = title,
          url = documentURL.toExternalForm()
        )
      TODO()
    }

    fun openAccountList() {
      TODO()
    }

    fun openDebugSettings() {
      TODO()
    }
  }
}
