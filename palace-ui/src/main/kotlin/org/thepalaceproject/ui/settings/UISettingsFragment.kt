package org.thepalaceproject.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import org.thepalaceproject.ui.R
import org.thepalaceproject.ui.UIMigration

class UISettingsFragment : Fragment(R.layout.settings) {

  private lateinit var debugOldUI: Button

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.debugOldUI =
      view.findViewById(R.id.settingsDebugNewUI)
  }

  override fun onStart() {
    super.onStart()

    this.debugOldUI.setOnClickListener {
      UIMigration.setRunningNewUI(
        context = this.requireContext(),
        running = false
      )

      val intent = Intent()
      intent.setAction(Intent.ACTION_MAIN)
      intent.addCategory(Intent.CATEGORY_LAUNCHER)
      intent.setPackage("org.thepalaceproject.palace")
      this.startActivity(intent)
      System.exit(0)
    }
  }

  override fun onStop() {
    super.onStop()
  }
}
