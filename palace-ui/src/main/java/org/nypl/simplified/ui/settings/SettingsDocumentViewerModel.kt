package org.nypl.simplified.ui.settings

object SettingsDocumentViewerModel {

  data class DocumentTarget(
    val title: String,
    val url: String
  )

  var documentTarget: DocumentTarget? = null
}
