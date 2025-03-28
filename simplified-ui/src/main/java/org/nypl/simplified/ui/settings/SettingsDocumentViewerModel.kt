package org.nypl.simplified.ui.settings

object SettingsDocumentViewerModel {

  data class Target(
    val title: String,
    val url: String
  )

  var target: Target? = null
}
