package org.nypl.simplified.ui.settings

import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.nypl.simplified.threads.UIThread

object SettingsListPreferenceDialog {
  /**
   * Opens a dialog with a list of selectable items.
   */
  @UiThread
  fun show(
    fragment: Fragment,
    title: CharSequence,
    entries: Array<String>,
    values: Array<String>,
    initialValue: String,
    onSelected: (String) -> Unit
  ) {
    UIThread.checkIsUIThread()

    require(entries.size == values.size) {
      "entries and values must have the same length"
    }

    val checkedIndex = values.indexOf(initialValue)
    MaterialAlertDialogBuilder(fragment.requireActivity())
      .setTitle(title)
      .setSingleChoiceItems(entries, checkedIndex) { _, which ->
        onSelected(values[which])
      }.show()
  }
}
