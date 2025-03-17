package org.nypl.simplified.ui.settings

sealed class SettingsDocumentViewerEvent {

  /*
   * The document viewer screen wants to close.
   */

  object GoUpwards : SettingsDocumentViewerEvent()
}
