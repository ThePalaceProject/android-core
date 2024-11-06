package org.nypl.simplified.ui.accounts.saml20

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.ProgressBar
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.io7m.jmulticlose.core.CloseableCollection
import org.librarysimplified.ui.accounts.R
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.webview.WebViewUtilities
import org.slf4j.LoggerFactory

/**
 * A fragment that performs the SAML 2.0 login workflow.
 */

class AccountSAML20Fragment : Fragment(R.layout.account_saml20) {

  private val logger =
    LoggerFactory.getLogger(AccountSAML20Fragment::class.java)

  private val listener: FragmentListenerType<AccountSAML20Event>
    by this.fragmentListeners()

  private lateinit var progress: ProgressBar
  private lateinit var webView: WebView

  @Volatile
  private var subscriptions = CloseableCollection.create()

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)
    this.progress = view.findViewById(R.id.saml20progressBar)
    this.webView = view.findViewById(R.id.saml20WebView)
    WebViewUtilities.setForcedDark(this.webView.settings, this.resources.configuration)
  }

  override fun onStart() {
    super.onStart()

    this.logger.debug("WebView URL: {}", this.webView.url)
    this.webView.webChromeClient = AccountSAML20ChromeClient(this.progress)
    this.webView.webViewClient = AccountSAML20Model.webViewClient()
    this.webView.settings.javaScriptEnabled = true

    this.subscriptions = CloseableCollection.create()
    this.subscriptions.add(
      AccountSAML20Model.state.subscribe { oldValue, newValue ->
        this.onStateChanged(newValue)
      }
    )
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.close()
  }

  @UiThread
  private fun onStateChanged(
    newValue: AccountSAML20State
  ) {
    when (newValue) {
      is AccountSAML20State.Failed -> {
        this.onStateFailed(newValue)
      }

      is AccountSAML20State.TokenObtained -> {
        this.onStateTokenObtained()
      }

      AccountSAML20State.WebViewInitialized -> {
        this.onStateWebViewInitialized()
      }

      AccountSAML20State.WebViewInitializing -> {
        this.onStateWebViewInitializing()
      }

      AccountSAML20State.WebViewRequestSent -> {
        this.onStateWebViewRequestSent()
      }
    }
  }

  @UiThread
  private fun onStateWebViewInitializing() {

  }

  @UiThread
  private fun onStateWebViewInitialized() {
    this.webView.loadUrl(this.constructLoginURI())
    AccountSAML20Model.setState(AccountSAML20State.WebViewRequestSent)
  }

  @UiThread
  private fun onStateFailed(
    failed: AccountSAML20State.Failed
  ) {
    val newDialog =
      MaterialAlertDialogBuilder(this.requireActivity())
        .setTitle(R.string.accountCreationFailed)
        .setMessage(R.string.accountCreationFailedMessage)
        .setPositiveButton(R.string.accountsDetails) { dialog, _ ->
          this.showErrorPage(this.makeLoginTaskSteps(failed.message))
          dialog.dismiss()
        }.create()
    newDialog.show()
  }

  @UiThread
  private fun onStateWebViewRequestSent() {

  }

  @UiThread
  private fun onStateTokenObtained() {

  }

  private fun constructLoginURI(): String {
    return buildString {
      this.append(AccountSAML20Model.authenticationURI())
      this.append("&redirect_uri=")
      this.append(AccountSAML20.callbackURI)
    }
  }

  private fun makeLoginTaskSteps(
    message: String
  ): List<TaskStep> {
    val taskRecorder = TaskRecorder.create()
    taskRecorder.beginNewStep("Started SAML 2.0 login...")
    taskRecorder.currentStepFailed(
      message = message,
      errorCode = "samlAccountCreationFailed",
      extraMessages = listOf()
    )
    return taskRecorder.finishFailure<AccountType>().steps
  }

  private fun showErrorPage(
    taskSteps: List<TaskStep>
  ) {
    val parameters =
      ErrorPageParameters(
        emailAddress = AccountSAML20Model.supportEmailAddress,
        body = "",
        subject = "[palace-error-report]",
        attributes = sortedMapOf(),
        taskSteps = taskSteps
      )

    this.listener.post(AccountSAML20Event.OpenErrorPage(parameters))
  }
}
