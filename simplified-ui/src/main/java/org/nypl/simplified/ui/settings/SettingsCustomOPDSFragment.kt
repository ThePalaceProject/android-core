package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.io7m.jmulticlose.core.CloseableCollection
import io.reactivex.android.schedulers.AndroidSchedulers
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * A fragment that allows to add a custom OPDS feed.
 */

class SettingsCustomOPDSFragment : Fragment(R.layout.settings_custom_opds) {

  private val logger =
    LoggerFactory.getLogger(SettingsCustomOPDSFragment::class.java)

  private var subscriptions =
    CloseableCollection.create()

  private lateinit var create: Button
  private lateinit var feedURL: EditText
  private lateinit var progress: ProgressBar
  private lateinit var progressText: TextView

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

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

    val uiObservable =
      Services.serviceDirectory()
        .requireService(ProfilesControllerType::class.java)
        .accountEvents()
        .observeOn(AndroidSchedulers.mainThread())

    this.subscriptions.add(AutoCloseable {
      uiObservable.subscribe(this::onAccountEvent)
    })
    this.subscriptions.add(
      SettingsCustomOPDSModel.taskRunning.subscribe { _, status ->
        this.onTaskRunningChanged(status)
      }
    )

    this.create.setOnClickListener {
      SettingsCustomOPDSModel.createCustomOPDSFeed(this.feedURL.text.trim().toString())
    }
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.close()
  }

  private fun onTaskRunningChanged(running: Boolean) {
    if (running) {
      this.create.isEnabled = false
      this.progress.visibility = View.VISIBLE
      this.progressText.text = ""
    } else {
      this.progress.visibility = View.INVISIBLE
      this.create.isEnabled = this.isValidURI()
    }
  }

  private fun onAccountEvent(event: AccountEvent) {
    this.progressText.append(event.message)
    this.progressText.append("\n")

    for (name in event.attributes.keys) {
      this.progressText.append("    ")
      this.progressText.append(name)
      this.progressText.append(": ")
      this.progressText.append(event.attributes[name])
      this.progressText.append("\n")
    }
  }

  private fun isValidURI(): Boolean {
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
}
