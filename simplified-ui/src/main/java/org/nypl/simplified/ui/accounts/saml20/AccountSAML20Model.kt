package org.nypl.simplified.ui.accounts.saml20

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.UiThread
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

object AccountSAML20Model {

  @Volatile
  private lateinit var description: AccountProviderAuthenticationDescription.SAML2_0

  @Volatile
  private lateinit var account: AccountID

  @Volatile
  private lateinit var webClient: AccountSAML20WebClient

  private val logger =
    LoggerFactory.getLogger(AccountSAML20Model::class.java)

  private val services =
    Services.serviceDirectory()

  private val buildConfig =
    services.requireService(BuildConfigurationServiceType::class.java)

  private val profilesController =
    services.requireService(ProfilesControllerType::class.java)

  private val uiThread =
    services.requireService(UIThreadServiceType::class.java)

  private val stateAttribute: AttributeType<AccountSAML20State> =
    Attributes.create { e -> logger.debug("Attribute exception: ", e) }
      .withValue(AccountSAML20State.WebViewInitializing)

  val state: AttributeReadableType<AccountSAML20State> =
    stateAttribute

  val supportEmailAddress: String
    get() = buildConfig.supportErrorReportEmailAddress

  /**
   * Yes, we really are storing a global reference to a web view.
   */

  private val webView: AtomicReference<WebView> = AtomicReference()

  fun setState(newState: AccountSAML20State) {
    uiThread.runOnUIThread {
      stateAttribute.set(newState)
    }
  }

  init {
    state.subscribe { oldValue, newValue ->
      onStateChanged(newValue)
    }
  }

  private fun onStateChanged(
    newValue: AccountSAML20State
  ) {
    when (newValue) {
      is AccountSAML20State.Failed -> {
        profilesController.profileAccountLogin(
          ProfileAccountLoginRequest.SAML20Cancel(
            accountId = newValue.accountID,
            description = newValue.description
          )
        )
      }

      is AccountSAML20State.TokenObtained -> {
        profilesController.profileAccountLogin(
          ProfileAccountLoginRequest.SAML20Complete(
            accountId = newValue.accountID,
            accessToken = newValue.token,
            patronInfo = newValue.patronInfo,
            cookies = newValue.cookies
          )
        )
      }

      is AccountSAML20State.WebViewInitialized -> {
        // Nothing to do
      }

      is AccountSAML20State.WebViewInitializing -> {
        // Nothing to do
      }

      is AccountSAML20State.WebViewRequestSent -> {
        // Nothing to do
      }
    }
  }

  @UiThread
  fun startAuthenticationProcess(
    application: Application,
    accountID: AccountID,
    authenticationDescription: AccountProviderAuthenticationDescription.SAML2_0,
    webViewDataDirectory: File
  ) {
    account =
      accountID
    description =
      authenticationDescription
    webClient =
      AccountSAML20WebClient(
        resources = application.resources,
        account = accountID,
        description = authenticationDescription,
        webViewDataDir = webViewDataDirectory
      )

    val view = WebView(application)
    view.webViewClient = webClient
    view.settings.javaScriptEnabled = true

    webView.set(view)
    setState(AccountSAML20State.WebViewInitializing)

    /*
     * We need to ensure that the web view starts in a fresh state with no cookies present.
     * There could be any number of existing cookies present, and any one of them can interfere
     * with the login process. The removal process is asynchronous, so we have to ask the web
     * view to tell us when removing is done so that we can start the web request(s).
     */

    CookieManager.getInstance().removeAllCookies {
      setState(AccountSAML20State.WebViewInitialized(webView.get()))
    }
  }

  fun authenticationURI(): URI {
    return description.authenticate
  }

  fun webViewClient(): WebViewClient {
    return webClient
  }
}
