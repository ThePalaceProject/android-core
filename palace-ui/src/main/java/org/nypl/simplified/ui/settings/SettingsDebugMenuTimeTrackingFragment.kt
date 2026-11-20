package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.books.time.tracking.TimeTrackingDebugInfo
import org.nypl.simplified.books.time.tracking.api.TimeTrackingServiceType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.thepalaceproject.db.api.DBType
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The time tracking screen of the debug menu.
 *
 * This screen displays a snapshot of the state of the time tracking pipeline: the current
 * session status, the spans and entries which are pending, flags for pipeline stages which
 * are not making progress, and the most recent events. The display is read only, and is
 * refreshed when the screen is shown and when the refresh button is pressed.
 */
class SettingsDebugMenuTimeTrackingFragment :
  Fragment(R.layout.debug_time_tracking),
  MainBackButtonConsumerType {
  companion object : ScreenDefinitionFactoryType<Unit, SettingsDebugMenuTimeTrackingFragment> {
    private class ScreenSettingsDebugMenu : ScreenDefinitionType<Unit, SettingsDebugMenuTimeTrackingFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() = Unit

      override fun fragment(): SettingsDebugMenuTimeTrackingFragment = SettingsDebugMenuTimeTrackingFragment()
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, SettingsDebugMenuTimeTrackingFragment> =
      ScreenSettingsDebugMenu()
  }

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result {
    MainNavigation.Settings.goUp()
    return BACK_BUTTON_CONSUMED
  }

  private var output: TextView? = null
  private var refreshButton: Button? = null
  private var toolbarBack: View? = null

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?,
  ) {
    super.onViewCreated(view, savedInstanceState)

    val out =
      view.findViewById<TextView>(R.id.debugTimeTrackingOutput)
    val refresh =
      view.findViewById<Button>(R.id.debugTimeTrackingRefresh)
    val back =
      view.findViewById<View>(R.id.debugToolbarBackIconTouch)

    this.output = out
    this.refreshButton = refresh
    this.toolbarBack = back

    back.setOnClickListener {
      this.onBackButtonPressed()
    }
    refresh.setOnClickListener {
      this.refreshNow()
    }
  }

  override fun onStart() {
    super.onStart()
    this.refreshNow()
  }

  private fun refreshNow() {
    val out =
      this.output
    if (out == null) {
      return
    }

    val services =
      Services.serviceDirectory()
    val database =
      services.requireService(DBType::class.java)
    val timeTracking =
      services.requireService(TimeTrackingServiceType::class.java)
    val now =
      OffsetDateTime.now(ZoneOffset.UTC)

    val text =
      TimeTrackingDebugInfo
        .lines(database = database, status = timeTracking.status(), now = now)
        .joinToString("\n")

    out.text = text
  }
}
