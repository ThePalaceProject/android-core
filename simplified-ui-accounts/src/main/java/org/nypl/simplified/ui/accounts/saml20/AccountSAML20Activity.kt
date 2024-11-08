package org.nypl.simplified.ui.accounts.saml20

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.io7m.jmulticlose.core.CloseableCollection
import org.librarysimplified.ui.accounts.R
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.ui.errorpage.ErrorPageActivity
import org.nypl.simplified.ui.errorpage.ErrorPageActivity.Companion.PARAMETER_ID
import org.nypl.simplified.ui.errorpage.ErrorPageParameters

class AccountSAML20Activity : AppCompatActivity(R.layout.saml20_activity) {

  private lateinit var progress: ProgressBar
  private lateinit var root: FrameLayout

  @Volatile
  private var subscriptions = CloseableCollection.create()

  override fun onStart() {
    super.onStart()

    this.root =
      this.findViewById(R.id.saml20Root)
    this.progress =
      this.findViewById(R.id.saml20progressBar)

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
        this.onStateTokenObtained(newValue)
      }

      is AccountSAML20State.WebViewInitialized -> {
        this.onStateWebViewInitialized(newValue)
      }

      is AccountSAML20State.WebViewInitializing -> {
        this.onStateWebViewInitializing()
      }

      is AccountSAML20State.WebViewRequestSent -> {
        this.onStateWebViewRequestSent(newValue)
      }
    }
  }

  @UiThread
  private fun onStateWebViewInitializing() {
    // Nothing to do
  }

  @UiThread
  private fun onStateWebViewInitialized(
    newValue: AccountSAML20State.WebViewInitialized
  ) {
    this.setWebView(newValue.webView)

    newValue.webView.loadUrl(this.constructLoginURI())
    AccountSAML20Model.setState(AccountSAML20State.WebViewRequestSent(newValue.webView))
  }

  @UiThread
  private fun onStateFailed(
    failed: AccountSAML20State.Failed
  ) {
    this.setWebView(failed.webView)

    val newDialog =
      MaterialAlertDialogBuilder(this)
        .setTitle(R.string.accountCreationFailed)
        .setMessage(R.string.accountCreationFailedMessage)
        .setPositiveButton(R.string.accountsDetails) { dialog, _ ->
          this.showErrorPage(this.makeLoginTaskSteps(failed.message))
          dialog.dismiss()
          this.finish()
        }.create()
    newDialog.show()
  }

  @UiThread
  private fun onStateWebViewRequestSent(
    newValue: AccountSAML20State.WebViewRequestSent
  ) {
    this.setWebView(newValue.webView)
  }

  @UiThread
  private fun onStateTokenObtained(
    newValue: AccountSAML20State.TokenObtained
  ) {
    this.setWebView(newValue.webView)
    this.finish()
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

    val bundle = Bundle()
    bundle.putSerializable(PARAMETER_ID, parameters)

    val intent = Intent(this, ErrorPageActivity::class.java)
    intent.putExtras(bundle)
    this.startActivity(intent)
  }

  private fun setWebView(
    webView: WebView
  ) {
    webView.webChromeClient = AccountSAML20ChromeClient(this.progress)

    val webViewLayout = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.MATCH_PARENT
    )
    webView.layoutParams = webViewLayout
    (webView.parent as ViewGroup?)?.removeView(webView)

    this.root.removeAllViews()
    this.root.addView(webView)
    this.root.addView(this.progress)
  }
}
