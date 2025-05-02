package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import com.io7m.jmulticlose.core.CloseableCollection
import org.librarysimplified.ui.R
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * A fragment that allows to add a custom OPDS feed.
 */

class SettingsCustomOPDSFragment : Fragment(R.layout.settings_custom_opds),
  MainBackButtonConsumerType {

  private val logger =
    LoggerFactory.getLogger(SettingsCustomOPDSFragment::class.java)

  private var subscriptions =
    CloseableCollection.create()

  private lateinit var toolbarBack: View
  private lateinit var create: Button
  private lateinit var feedURL: EditText
  private lateinit var progress: ProgressBar
  private lateinit var progressText: TextView

  companion object : ScreenDefinitionFactoryType<Unit, SettingsCustomOPDSFragment> {
    private class ScreenSettingsCustomOPDS :
      ScreenDefinitionType<Unit, SettingsCustomOPDSFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() {
        return Unit
      }

      override fun fragment(): SettingsCustomOPDSFragment {
        return SettingsCustomOPDSFragment()
      }
    }

    override fun createScreenDefinition(
      p: Unit
    ): ScreenDefinitionType<Unit, SettingsCustomOPDSFragment> {
      return ScreenSettingsCustomOPDS()
    }
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbarBack =
      view.findViewById(R.id.settingsCustomOPDSToolbarBackIconTouch)
    this.feedURL =
      view.findViewById(R.id.settingsCustomOPDSURL)
    this.create =
      view.findViewById(R.id.settingsCustomOPDSCreate)
    this.progress =
      view.findViewById(R.id.settingsCustomOPDSProgressBar)
    this.progressText =
      view.findViewById(R.id.settingsCustomOPDSProgressText)

    if (savedInstanceState == null) {
      this.progressText.text = ""
    }
  }

  override fun onStart() {
    super.onStart()

    this.feedURL.addTextChangedListener(this.URITextWatcher())

    this.subscriptions =
      CloseableCollection.create()

    this.subscriptions.add(
      SettingsCustomOPDSModel.taskRunningUI.subscribe { _, status ->
        this.onTaskRunningChanged(status)
      }
    )
    this.subscriptions.add(
      SettingsCustomOPDSModel.taskUI.subscribe { _, status ->
        this.onTaskMessages(status)
      }
    )

    this.toolbarBack.setOnClickListener {
      this.toolbarBack.postDelayed(MainNavigation.Settings::goUp, 500)
    }

    this.create.setOnClickListener {
      SettingsCustomOPDSModel.createCustomOPDSFeed(this.feedURL.text.trim().toString())
    }
  }

  @UiThread
  private fun onTaskMessages(
    status: List<String>
  ) {
    UIThread.checkIsUIThread()

    for (s in status) {
      this.progressText.append(s)
      this.progressText.append("\n")
    }
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.close()
  }

  @UiThread
  private fun onTaskRunningChanged(running: Boolean) {
    UIThread.checkIsUIThread()

    if (running) {
      this.create.isEnabled = false
      this.progress.visibility = View.VISIBLE
      this.progressText.text = ""
    } else {
      this.progress.visibility = View.INVISIBLE
      this.create.isEnabled = this.isValidURI()
    }
  }

  @UiThread
  private fun isValidURI(): Boolean {
    UIThread.checkIsUIThread()

    val text = this.feedURL.text
    return if (text.isNotEmpty()) {
      try {
        URI(text.toString())
        this.feedURL.setError(null, null)
        true
      } catch (e: Exception) {
        this.logger.debug("not a valid URI: ", e)
        this.feedURL.error = this.resources.getString(R.string.settingsCustomOPDSInvalidURI)
        false
      }
    } else {
      false
    }
  }

  inner class URITextWatcher : TextWatcher {
    override fun afterTextChanged(s: Editable?) {
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
      this@SettingsCustomOPDSFragment.create.isEnabled =
        this@SettingsCustomOPDSFragment.isValidURI()
    }
  }

  override fun onBackButtonPressed(): Result {
    MainNavigation.Settings.goUp()
    return BACK_BUTTON_CONSUMED
  }
}
