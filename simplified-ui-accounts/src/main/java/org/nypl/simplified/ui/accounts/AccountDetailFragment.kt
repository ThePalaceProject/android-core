package org.nypl.simplified.ui.accounts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.gms.location.LocationServices
import com.io7m.junreachable.UnimplementedCodeException
import com.io7m.junreachable.UnreachableCodeException
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingIn
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingInWaitingForExternalAuthentication
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggingOut
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoginFailed
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLogoutFailed
import org.nypl.simplified.accounts.api.AccountLoginState.AccountNotLoggedIn
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountProviderAuthenticationDescription
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.android.ktx.supportActionBar
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.oauth.OAuthCallbackIntentParsing
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest.Basic
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest.BasicToken
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest.OAuthWithIntermediaryCancel
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest.OAuthWithIntermediaryInitiate
import org.nypl.simplified.bookmarks.api.BookmarkSyncEnableResult.SYNC_DISABLED
import org.nypl.simplified.bookmarks.api.BookmarkSyncEnableResult.SYNC_ENABLED
import org.nypl.simplified.bookmarks.api.BookmarkSyncEnableResult.SYNC_ENABLE_NOT_SUPPORTED
import org.nypl.simplified.bookmarks.api.BookmarkSyncEnableStatus
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus.AsCancelButtonDisabled
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus.AsCancelButtonEnabled
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus.AsLoginButtonDisabled
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus.AsLoginButtonEnabled
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus.AsLogoutButtonDisabled
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus.AsLogoutButtonEnabled
import org.nypl.simplified.ui.images.ImageAccountIcons
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.neutrality.NeutralToolbar
import org.slf4j.LoggerFactory
import java.net.URI
import org.librarysimplified.ui.accounts.R

/**
 * A fragment that shows settings for a single account.
 */

class AccountDetailFragment : Fragment(R.layout.account) {

  private val logger =
    LoggerFactory.getLogger(AccountDetailFragment::class.java)

  private val subscriptions: CompositeDisposable =
    CompositeDisposable()

  private val listener: FragmentListenerType<AccountDetailEvent> by fragmentListeners()

  private val parameters: AccountFragmentParameters by lazy {
    this.requireArguments()[PARAMETERS_ID] as AccountFragmentParameters
  }

  private val services = Services.serviceDirectory()

  private val viewModel: AccountDetailViewModel by viewModels(
    factoryProducer = {
      AccountDetailViewModelFactory(
        account = this.parameters.accountID,
        listener = this.listener
      )
    }
  )

  private val fusedLocationClient by lazy {
    LocationServices.getFusedLocationProviderClient(requireActivity())
  }

  private val imageLoader: ImageLoaderType =
    services.requireService(ImageLoaderType::class.java)

  private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
  )

  private val locationPermissionCallback =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
      if (results.values.all { it }) {
        openCardCreatorWebView()
      } else if (!shouldShowRationale()) {
        showSettingsDialog()
      }
    }

  private lateinit var accountCustomOPDS: ViewGroup
  private lateinit var accountCustomOPDSField: TextView
  private lateinit var accountEULA: TextView
  private lateinit var accountIcon: ImageView
  private lateinit var accountLicenses: ViewGroup
  private lateinit var accountPrivacyPolicy: ViewGroup
  private lateinit var accountSubtitle: TextView
  private lateinit var accountTitle: TextView
  private lateinit var authentication: ViewGroup
  private lateinit var authenticationAlternatives: ViewGroup
  private lateinit var authenticationAlternativesButtons: ViewGroup
  private lateinit var authenticationViews: AccountAuthenticationViews
  private lateinit var bookmarkSync: ViewGroup
  private lateinit var bookmarkSyncCheck: SwitchCompat
  private lateinit var bookmarkSyncProgress: ProgressBar
  private lateinit var loginButtonErrorDetails: Button
  private lateinit var loginProgress: ViewGroup
  private lateinit var loginProgressBar: ProgressBar
  private lateinit var loginProgressText: TextView
  private lateinit var loginTitle: ViewGroup
  private lateinit var reportIssueEmail: TextView
  private lateinit var reportIssueGroup: ViewGroup
  private lateinit var reportIssueItem: View
  private lateinit var settingsCardCreator: ConstraintLayout
  private lateinit var signUpButton: Button
  private lateinit var signUpLabel: TextView
  private lateinit var toolbar: NeutralToolbar

  private val imageButtonLoadingTag = "IMAGE_BUTTON_LOADING"
  private val nyplCardCreatorScheme = "nypl.card-creator"

  companion object {

    private const val PARAMETERS_ID =
      "org.nypl.simplified.ui.accounts.AccountFragment.parameters"

    /**
     * Create a new account fragment for the given parameters.
     */

    fun create(parameters: AccountFragmentParameters): AccountDetailFragment {
      val fragment = AccountDetailFragment()
      fragment.arguments = bundleOf(this.PARAMETERS_ID to parameters)
      return fragment
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.accountEULA =
      view.findViewById(R.id.accountEULA)
    this.accountTitle =
      view.findViewById(R.id.accountCellTitle)
    this.accountSubtitle =
      view.findViewById(R.id.accountCellSubtitle)
    this.accountIcon =
      view.findViewById(R.id.accountCellIcon)
    this.toolbar =
      view.rootView.findViewWithTag(NeutralToolbar.neutralToolbarName)

    this.authentication =
      view.findViewById(R.id.auth)
    this.authenticationViews =
      AccountAuthenticationViews(
        viewGroup = this.authentication,
        onUsernamePasswordChangeListener = this::onBasicUserPasswordChanged
      )

    this.authenticationAlternatives =
      view.findViewById(R.id.accountAuthAlternatives)
    this.authenticationAlternativesButtons =
      view.findViewById(R.id.accountAuthAlternativesButtons)

    this.bookmarkSyncProgress =
      view.findViewById(R.id.accountSyncProgress)
    this.bookmarkSync =
      view.findViewById(R.id.accountSyncBookmarks)
    this.bookmarkSyncCheck =
      this.bookmarkSync.findViewById(R.id.accountSyncBookmarksCheck)

    this.loginTitle =
      view.findViewById(R.id.accountTitleAnnounce)
    this.loginProgress =
      view.findViewById(R.id.accountLoginProgress)
    this.loginProgressBar =
      view.findViewById(R.id.accountLoginProgressBar)
    this.loginProgressText =
      view.findViewById(R.id.accountLoginProgressText)
    this.loginButtonErrorDetails =
      view.findViewById(R.id.accountLoginButtonErrorDetails)
    this.signUpButton =
      view.findViewById(R.id.accountCardCreatorSignUp)
    this.signUpLabel =
      view.findViewById(R.id.accountCardCreatorLabel)
    this.settingsCardCreator =
      view.findViewById(R.id.accountCardCreator)

    this.accountCustomOPDS =
      view.findViewById(R.id.accountCustomOPDS)
    this.accountCustomOPDSField =
      this.accountCustomOPDS.findViewById(R.id.accountCustomOPDSField)
    this.accountPrivacyPolicy =
      view.findViewById(R.id.accountPrivacyPolicy)
    this.accountLicenses =
      view.findViewById(R.id.accountLicenses)

    this.reportIssueGroup =
      view.findViewById(R.id.accountReportIssue)
    this.reportIssueItem =
      this.reportIssueGroup.findViewById(R.id.accountReportIssueText)
    this.reportIssueEmail =
      this.reportIssueGroup.findViewById(R.id.accountReportIssueEmail)

    if (this.parameters.showPleaseLogInTitle) {
      this.loginTitle.visibility = View.VISIBLE
    } else {
      this.loginTitle.visibility = View.GONE
    }

    /*
     * Instantiate views for alternative authentication methods.
     */

    this.authenticationAlternativesMake()

    ImageAccountIcons.loadAccountLogoIntoView(
      this.imageLoader.loader,
      this.viewModel.account.provider.toDescription(),
      R.drawable.account_default,
      this.accountIcon
    )

    this.viewModel.accountLive.observe(this.viewLifecycleOwner) {
      this.reconfigureAccountUI()
    }

    this.viewModel.accountSyncingSwitchStatus.observe(this.viewLifecycleOwner) { status ->
      this.reconfigureBookmarkSyncingSwitch(status)
    }
  }

  private fun reconfigureBookmarkSyncingSwitch(status: BookmarkSyncEnableStatus) {
    /*
     * Remove the checked-change listener, because setting `isChecked` will trigger the listener.
     */

    this.bookmarkSyncCheck.setOnCheckedChangeListener(null)

    /*
     * Otherwise, the switch is doing something that interests us...
     */

    val account = this.viewModel.account
    return when (status) {
      is BookmarkSyncEnableStatus.Changing -> {
        this.bookmarkSyncProgress.visibility = View.VISIBLE
        this.bookmarkSyncCheck.isEnabled = false
      }

      is BookmarkSyncEnableStatus.Idle -> {
        this.bookmarkSyncProgress.visibility = View.INVISIBLE

        when (status.status) {
          SYNC_ENABLE_NOT_SUPPORTED -> {
            this.bookmarkSyncCheck.isChecked = false
            this.bookmarkSyncCheck.isEnabled = false
          }

          SYNC_ENABLED,
          SYNC_DISABLED -> {
            val isPermitted = account.preferences.bookmarkSyncingPermitted
            val isSupported = account.loginState.credentials?.annotationsURI != null

            this.bookmarkSyncCheck.isChecked = isPermitted
            this.bookmarkSyncCheck.isEnabled = isSupported

            this.bookmarkSyncCheck.setOnCheckedChangeListener { _, isChecked ->
              this.viewModel.enableBookmarkSyncing(isChecked)
            }
          }
        }
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    this.cancelImageButtonLoading()
    this.imageLoader.loader.cancelRequest(this.accountIcon)
  }

  private fun onBasicUserPasswordChanged(
    username: AccountUsername,
    password: AccountPassword
  ) {
    this.setLoginButtonStatus(this.determineLoginIsSatisfied())
  }

  private fun determineLoginIsSatisfied(): AccountLoginButtonStatus {
    val authDescription = this.viewModel.account.provider.authentication
    val loginPossible = authDescription.isLoginPossible
    val satisfiedFor = this.authenticationViews.isSatisfiedFor(authDescription)

    return if (loginPossible && satisfiedFor) {
      AsLoginButtonEnabled {
        this.loginFormLock()
        this.tryLogin()
      }
    } else {
      AsLoginButtonDisabled
    }
  }

  /**
   * If there's any card creator URI, the button should be enabled...
   */

  private fun shouldSignUpBeEnabled(): Boolean {
    return viewModel.account.provider.cardCreatorURI != null
  }

  override fun onStart() {
    super.onStart()

    this.configureToolbar()

    /*
     * Configure the COPPA age gate switch. If the user changes their age, a log out
     * is required.
     */

    this.authenticationViews.setCOPPAState(
      isOver13 = this.viewModel.isOver13,
      onAgeCheckboxClicked = this.onAgeCheckboxClicked()
    )

    /*
     * Launch Card Creator
     */

    this.signUpButton.setOnClickListener {
      if (!isLocationPermissionGranted()) {
        if (shouldShowRationale()) {
          showLocationDisclaimerDialog()
        } else {
          requestLocationPermissions()
        }
      } else {
        openCardCreatorWebView()
      }
    }

    /*
     * Configure the bookmark syncing switch to enable/disable syncing permissions.
     */

    this.bookmarkSyncCheck.setOnCheckedChangeListener { _, isChecked ->
      this.viewModel.enableBookmarkSyncing(isChecked)
    }

    /*
     * Configure the "Report issue..." item.
     */

    this.configureReportIssue()

    /*
     * Populate the barcode if passed in (e.g. via deep link).
     */

    val barcode = this.parameters.barcode
    if (barcode == null) {
      this.authenticationViews.setBasicUserAndPass("", "")
    } else {
      this.authenticationViews.setBasicUserAndPass(
        user = barcode,
        password = ""
      )
    }

    /*
     * Hide the toolbar and back arrow if there is no page to return to (e.g. coming from a deep link).
     */
    if (this.parameters.hideToolbar) {
      this.toolbar.visibility = View.GONE
    } else {
      this.toolbar.visibility = View.VISIBLE
    }
  }

  private fun instantiateAlternativeAuthenticationViews() {
    for (alternative in this.viewModel.account.provider.authenticationAlternatives) {
      when (alternative) {
        is AccountProviderAuthenticationDescription.COPPAAgeGate -> {
          this.logger.warn("COPPA age gate is not currently supported as an alternative.")
        }

        is AccountProviderAuthenticationDescription.Basic -> {
          this.logger.warn("Basic authentication is not currently supported as an alternative.")
        }

        is AccountProviderAuthenticationDescription.BasicToken -> {
          this.logger.warn("Basic token authentication is not currently supported as an alternative.")
        }

        AccountProviderAuthenticationDescription.Anonymous -> {
          this.logger.warn("Anonymous authentication makes no sense as an alternative.")
        }

        is AccountProviderAuthenticationDescription.SAML2_0 -> {
          this.logger.warn("SAML 2.0 is not currently supported as an alternative.")
        }

        is AccountProviderAuthenticationDescription.OAuthWithIntermediary -> {
          val layout =
            this.layoutInflater.inflate(
              R.layout.auth_oauth,
              this.authenticationAlternativesButtons,
              false
            )

          this.configureImageButton(
            container = layout.findViewById(R.id.authOAuthIntermediaryLogo),
            buttonText = layout.findViewById(R.id.authOAuthIntermediaryLogoText),
            buttonImage = layout.findViewById(R.id.authOAuthIntermediaryLogoImage),
            text = this.getString(R.string.accountLoginWith, alternative.description),
            logoURI = alternative.logoURI,
            onClick = {
              this.onTryOAuthLogin(alternative)
            }
          )
          this.authenticationAlternativesButtons.addView(layout)
        }
      }
    }
  }

  /**
   * If there's a support url, enable an option to use it.
   */

  private fun configureReportIssue() {
    val supportUrl = this.viewModel.account.provider.supportEmail

    if (supportUrl != null) {
      this.reportIssueGroup.visibility = View.VISIBLE
      this.reportIssueEmail.text = supportUrl.replace("mailto:", "")
      this.reportIssueGroup.setOnClickListener {
        val intent = if (supportUrl.startsWith("mailto:")) {
          Intent(Intent.ACTION_SENDTO, Uri.parse(supportUrl))
        } else if (URLUtil.isValidUrl(supportUrl)) {
          Intent(Intent.ACTION_VIEW, Uri.parse(supportUrl))
        } else {
          null
        }

        if (intent != null) {
          val chosenIntent =
            Intent.createChooser(intent, this.resources.getString(R.string.accountReportIssue))

          try {
            this.startActivity(chosenIntent)
          } catch (e: Exception) {
            this.logger.error("unable to start activity: ", e)
            val context = this.requireContext()
            AlertDialog.Builder(context)
              .setMessage(context.getString(R.string.accountReportFailed, supportUrl))
              .create()
              .show()
          }
        } else {
          this.reportIssueGroup.visibility = View.GONE
        }
      }
    } else {
      this.reportIssueGroup.visibility = View.GONE
    }
  }

  private fun configureImageButton(
    container: ViewGroup,
    buttonText: TextView,
    buttonImage: ImageView,
    text: String,
    logoURI: URI?,
    onClick: () -> Unit
  ) {
    buttonText.text = text
    buttonText.setOnClickListener { onClick.invoke() }
    buttonImage.setOnClickListener { onClick.invoke() }
    this.loadAuthenticationLogoIfNecessary(
      uri = logoURI,
      view = buttonImage,
      onSuccess = {
        container.background = null
        buttonImage.visibility = View.VISIBLE
        buttonText.visibility = View.GONE
      }
    )
  }

  private fun onTrySAML2Login(
    authenticationDescription: AccountProviderAuthenticationDescription.SAML2_0
  ) {
    this.viewModel.tryLogin(
      ProfileAccountLoginRequest.SAML20Initiate(
        accountId = this.parameters.accountID,
        description = authenticationDescription
      )
    )

    this.listener.post(
      AccountDetailEvent.OpenSAML20Login(this.parameters.accountID, authenticationDescription)
    )
  }

  private fun onTryOAuthLogin(
    authenticationDescription: AccountProviderAuthenticationDescription.OAuthWithIntermediary
  ) {
    this.viewModel.tryLogin(
      OAuthWithIntermediaryInitiate(
        accountId = this.viewModel.account.id,
        description = authenticationDescription
      )
    )
    this.sendOAuthIntent(authenticationDescription)
  }

  private fun onTryBasicLogin(description: AccountProviderAuthenticationDescription.Basic) {
    val accountPassword: AccountPassword =
      this.authenticationViews.getBasicPassword()
    val accountUsername: AccountUsername =
      this.authenticationViews.getBasicUser()

    val request =
      Basic(
        accountId = this.viewModel.account.id,
        description = description,
        password = accountPassword,
        username = accountUsername
      )

    this.viewModel.tryLogin(request)
  }

  private fun onTryBasicTokenLogin(
    description: AccountProviderAuthenticationDescription.BasicToken
  ) {
    val accountPassword: AccountPassword =
      this.authenticationViews.getBasicTokenPassword()
    val accountUsername: AccountUsername =
      this.authenticationViews.getBasicTokenUser()

    val request =
      BasicToken(
        accountId = this.viewModel.account.id,
        description = description,
        password = accountPassword,
        username = accountUsername
      )

    this.viewModel.tryLogin(request)
  }

  private fun sendOAuthIntent(
    authenticationDescription: AccountProviderAuthenticationDescription.OAuthWithIntermediary
  ) {
    val callbackScheme =
      this.viewModel.buildConfig.oauthCallbackScheme.scheme
    val callbackUrl =
      OAuthCallbackIntentParsing.createUri(
        requiredScheme = callbackScheme,
        accountId = this.viewModel.account.id.uuid
      )

    /*
     * XXX: Is this correct for any other intermediary besides Clever?
     */

    val url = buildString {
      this.append(authenticationDescription.authenticate)
      this.append("&redirect_uri=$callbackUrl")
    }

    val i = Intent(Intent.ACTION_VIEW)
    i.data = Uri.parse(url)
    this.startActivity(i)
  }

  private fun configureToolbar() {
    val providerName = this.viewModel.account.provider.displayName
    val actionBar = this.supportActionBar ?: return
    actionBar.show()
    actionBar.setDisplayHomeAsUpEnabled(true)
    actionBar.setHomeActionContentDescription(null)
    actionBar.setTitle(providerName)
    this.toolbar.setLogoOnClickListener {
      this.listener.post(AccountDetailEvent.GoUpwards)
    }
  }

  override fun onStop() {
    super.onStop()

    /*
     * Broadcast the login state. The reason for doing this is that consumers might be subscribed
     * to the account so that they can perform actions when the user has either attempted to log
     * in, or has cancelled without attempting it. The consumers have no way to detect the fact
     * that the user didn't even try to log in unless we tell the account to broadcast its current
     * state.
     */

    this.logger.debug("broadcasting login state")
    this.viewModel.account.setLoginState(this.viewModel.account.loginState)

    this.subscriptions.clear()
  }

  private fun reconfigureAccountUI() {
    this.authenticationViews.showFor(this.viewModel.account.provider.authentication)

    if (this.viewModel.account.provider.cardCreatorURI != null) {
      this.settingsCardCreator.visibility = View.VISIBLE
    }

    this.accountTitle.text =
      this.viewModel.account.provider.displayName
    this.accountSubtitle.text =
      this.viewModel.account.provider.subtitle

    this.bookmarkSync.visibility = if (this.viewModel.account.requiresCredentials) {
      View.VISIBLE
    } else {
      View.GONE
    }

    /*
     * Only show a EULA if there's actually a EULA.
     */
    val eula = this.viewModel.eula
    if (eula != null) {
      this.accountEULA.visibility = View.VISIBLE
      this.accountEULA.setOnClickListener {
        this.listener.post(
          AccountDetailEvent.OpenDocViewer(
            getString(R.string.accountEULA),
            eula.readableURL
          )
        )
      }
    } else {
      this.accountEULA.visibility = View.GONE
    }

    val privacyPolicy = this.viewModel.privacyPolicy
    if (privacyPolicy != null) {
      this.accountPrivacyPolicy.visibility = View.VISIBLE
      this.accountPrivacyPolicy.setOnClickListener {
        this.listener.post(
          AccountDetailEvent.OpenDocViewer(
            getString(R.string.accountPrivacyPolicy),
            privacyPolicy.readableURL
          )
        )
      }
    } else {
      this.accountPrivacyPolicy.visibility = View.GONE
    }

    val licenses = this.viewModel.licenses
    if (licenses != null) {
      this.accountLicenses.visibility = View.VISIBLE
      this.accountLicenses.setOnClickListener {
        this.listener.post(
          AccountDetailEvent.OpenDocViewer(
            getString(R.string.accountLicenses),
            licenses.readableURL
          )
        )
      }
    } else {
      this.accountLicenses.visibility = View.GONE
    }

    /*
     * Conditionally enable sign up button
     */

    val signUpEnabled = this.shouldSignUpBeEnabled()
    this.signUpButton.isEnabled = signUpEnabled
    this.signUpLabel.isEnabled = signUpEnabled

    /*
     * Show/hide the custom OPDS feed section.
     */

    val catalogURIOverride = this.viewModel.account.preferences.catalogURIOverride
    this.accountCustomOPDSField.text = catalogURIOverride?.toString() ?: ""
    this.accountCustomOPDS.visibility =
      if (catalogURIOverride != null) {
        View.VISIBLE
      } else {
        View.GONE
      }

    this.disableSyncSwitchForLoginState(this.viewModel.account.loginState)

    return when (val loginState = this.viewModel.account.loginState) {
      AccountNotLoggedIn -> {
        this.loginProgress.visibility = View.GONE
        this.setLoginButtonStatus(
          AsLoginButtonEnabled {
            this.loginFormLock()
            this.tryLogin()
          }
        )

        if (this.viewModel.pendingLogout) {
          this.authenticationViews.setBasicUserAndPass("", "")
          this.viewModel.pendingLogout = false
        }
        this.loginFormUnlock()
      }

      is AccountLoggingIn -> {
        this.loginProgress.visibility = View.VISIBLE
        this.loginProgressBar.visibility = View.VISIBLE
        this.loginProgressText.text = loginState.status
        this.loginButtonErrorDetails.visibility = View.GONE
        this.loginFormLock()

        if (loginState.cancellable) {
          this.setLoginButtonStatus(
            AsCancelButtonEnabled {
              // We don't really support this yet.
              throw UnimplementedCodeException()
            }
          )
        } else {
          this.setLoginButtonStatus(AsCancelButtonDisabled)
        }
      }

      is AccountLoggingInWaitingForExternalAuthentication -> {
        this.loginProgress.visibility = View.VISIBLE
        this.loginProgressBar.visibility = View.VISIBLE
        this.loginProgressText.text = loginState.status
        this.loginButtonErrorDetails.visibility = View.GONE
        this.loginFormLock()
        this.setLoginButtonStatus(
          AsCancelButtonEnabled {
            this.viewModel.tryLogin(
              OAuthWithIntermediaryCancel(
                accountId = this.viewModel.account.id,
                description = loginState.description as AccountProviderAuthenticationDescription.OAuthWithIntermediary
              )
            )
          }
        )
      }

      is AccountLoginFailed -> {
        this.loginProgress.visibility = View.VISIBLE
        this.loginProgressBar.visibility = View.GONE
        this.loginProgressText.text = loginState.taskResult.steps.last().resolution.message
        this.loginFormUnlock()
        this.cancelImageButtonLoading()
        this.setLoginButtonStatus(
          AsLoginButtonEnabled {
            this.loginFormLock()
            this.tryLogin()
          }
        )
        this.loginButtonErrorDetails.visibility = View.VISIBLE
        this.loginButtonErrorDetails.setOnClickListener {
          this.viewModel.openErrorPage(loginState.taskResult.steps)
        }
        this.authenticationAlternativesShow()
      }

      is AccountLoggedIn -> {
        when (val creds = loginState.credentials) {
          is AccountAuthenticationCredentials.Basic -> {
            this.authenticationViews.setBasicUserAndPass(
              user = creds.userName.value,
              password = creds.password.value
            )
          }

          is AccountAuthenticationCredentials.BasicToken -> {
            this.authenticationViews.setBasicTokenUserAndPass(
              user = creds.userName.value,
              password = creds.password.value
            )
          }

          is AccountAuthenticationCredentials.OAuthWithIntermediary,
          is AccountAuthenticationCredentials.SAML2_0 -> {
            // Nothing
          }
        }

        this.loginProgress.visibility = View.GONE
        this.loginFormLock()
        this.loginButtonErrorDetails.visibility = View.GONE
        this.setLoginButtonStatus(
          AsLogoutButtonEnabled {
            this.loginFormLock()
            this.viewModel.tryLogout()
          }
        )
        this.authenticationAlternativesHide()
      }

      is AccountLoggingOut -> {
        when (val creds = loginState.credentials) {
          is AccountAuthenticationCredentials.Basic -> {
            this.authenticationViews.setBasicUserAndPass(
              user = creds.userName.value,
              password = creds.password.value
            )
          }

          is AccountAuthenticationCredentials.BasicToken -> {
            this.authenticationViews.setBasicTokenUserAndPass(
              user = creds.userName.value,
              password = creds.password.value
            )
          }

          is AccountAuthenticationCredentials.OAuthWithIntermediary,
          is AccountAuthenticationCredentials.SAML2_0 -> {
            // No UI
          }
        }

        this.loginProgress.visibility = View.VISIBLE
        this.loginButtonErrorDetails.visibility = View.GONE
        this.loginProgressBar.visibility = View.VISIBLE
        this.loginProgressText.text = loginState.status
        this.loginFormLock()
        this.setLoginButtonStatus(AsLogoutButtonDisabled)
      }

      is AccountLogoutFailed -> {
        when (val creds = loginState.credentials) {
          is AccountAuthenticationCredentials.Basic -> {
            this.authenticationViews.setBasicUserAndPass(
              user = creds.userName.value,
              password = creds.password.value
            )
          }

          is AccountAuthenticationCredentials.BasicToken -> {
            this.authenticationViews.setBasicTokenUserAndPass(
              user = creds.userName.value,
              password = creds.password.value
            )
          }

          is AccountAuthenticationCredentials.OAuthWithIntermediary,
          is AccountAuthenticationCredentials.SAML2_0 -> {
            // No UI
          }
        }

        this.loginProgress.visibility = View.VISIBLE
        this.loginProgressBar.visibility = View.GONE
        this.loginProgressText.text = loginState.taskResult.steps.last().resolution.message
        this.cancelImageButtonLoading()
        this.loginFormLock()
        this.setLoginButtonStatus(
          AsLogoutButtonEnabled {
            this.loginFormLock()
            this.viewModel.tryLogout()
          }
        )

        this.loginButtonErrorDetails.visibility = View.VISIBLE
        this.loginButtonErrorDetails.setOnClickListener {
          this.viewModel.openErrorPage(loginState.taskResult.steps)
        }
      }
    }
  }

  private fun disableSyncSwitchForLoginState(loginState: AccountLoginState) {
    return when (loginState) {
      is AccountLoggedIn -> {
      }

      is AccountLoggingIn,
      is AccountLoggingInWaitingForExternalAuthentication,
      is AccountLoggingOut,
      is AccountLoginFailed,
      is AccountLogoutFailed,
      AccountNotLoggedIn -> {
        this.bookmarkSyncCheck.setOnCheckedChangeListener(null)
        this.bookmarkSyncCheck.isChecked = false
        this.bookmarkSyncCheck.isEnabled = false
      }
    }
  }

  private fun cancelImageButtonLoading() {
    this.imageLoader.loader.cancelTag(this.imageButtonLoadingTag)
  }

  private fun setLoginButtonStatus(
    status: AccountLoginButtonStatus
  ) {
    this.authenticationViews.setLoginButtonStatus(status)

    val supportUrl = this.viewModel.account.provider.supportEmail
    val resetPasswordURI = this.viewModel.account.provider.resetPasswordURI

    this.authenticationViews.setResetPasswordLabelStatus(
      status,
      isVisible = resetPasswordURI != null,
      onClick = {
        try {
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resetPasswordURI.toString()))
          this.startActivity(intent)
        } catch (e: Exception) {
          this.logger.error("unable to start activity: ", e)
          val context = this.requireContext()
          AlertDialog.Builder(context)
            .setMessage(context.getString(R.string.accountPasswordResetFailed, supportUrl))
            .create()
            .show()
        }
      }
    )

    return when (status) {
      is AsLoginButtonEnabled -> {
        this.signUpLabel.setText(R.string.accountCardCreatorLabel)
      }

      is AsLoginButtonDisabled -> {
        this.signUpLabel.setText(R.string.accountCardCreatorLabel)
        this.signUpLabel.isEnabled = true
      }

      is AsLogoutButtonEnabled -> {
        this.signUpLabel.setText(R.string.accountWantChildCard)
        val enableSignup = shouldSignUpBeEnabled()
        this.signUpLabel.isEnabled = false
        this.signUpButton.isEnabled = false
        this.signUpLabel.setText(R.string.accountCardCreatorLabel)
      }

      is AsLogoutButtonDisabled -> {
        this.signUpLabel.setText(R.string.accountCardCreatorLabel)
      }

      is AsCancelButtonEnabled,
      AsCancelButtonDisabled -> {
        // Nothing
      }
    }
  }

  private fun loadAuthenticationLogoIfNecessary(
    uri: URI?,
    view: ImageView,
    onSuccess: () -> Unit
  ) {
    if (uri != null) {
      view.setImageDrawable(null)
      view.visibility = View.VISIBLE
      this.imageLoader.loader.load(uri.toString())
        .fit()
        .tag(this.imageButtonLoadingTag)
        .into(
          view,
          object : com.squareup.picasso.Callback {
            override fun onSuccess() {
              onSuccess.invoke()
            }

            override fun onError(e: Exception) {
              this@AccountDetailFragment.logger.error("failed to load authentication logo: ", e)
              view.visibility = View.GONE
            }
          }
        )
    }
  }

  private fun loginFormLock() {
    this.authenticationViews.setCOPPAState(
      isOver13 = this.viewModel.isOver13,
      onAgeCheckboxClicked = this.onAgeCheckboxClicked()
    )

    this.authenticationViews.lock()

    this.setLoginButtonStatus(AsLoginButtonDisabled)
    this.authenticationAlternativesHide()
  }

  private fun loginFormUnlock() {
    this.authenticationViews.setCOPPAState(
      isOver13 = this.viewModel.isOver13,
      onAgeCheckboxClicked = this.onAgeCheckboxClicked()
    )

    this.authenticationViews.unlock()

    val loginSatisfied = this.determineLoginIsSatisfied()
    this.setLoginButtonStatus(loginSatisfied)
    this.authenticationAlternativesShow()
    if (shouldSignUpBeEnabled()) {
      this.signUpButton.isEnabled = true
      this.signUpLabel.isEnabled = true
    }
  }

  private fun authenticationAlternativesMake() {
    this.authenticationAlternativesButtons.removeAllViews()
    if (this.viewModel.account.provider.authenticationAlternatives.isEmpty()) {
      this.authenticationAlternativesHide()
    } else {
      this.instantiateAlternativeAuthenticationViews()
      this.authenticationAlternativesShow()
    }
  }

  private fun authenticationAlternativesShow() {
    if (this.viewModel.account.provider.authenticationAlternatives.isNotEmpty()) {
      this.authenticationAlternatives.visibility = View.VISIBLE
    }
  }

  private fun authenticationAlternativesHide() {
    this.authenticationAlternatives.visibility = View.GONE
  }

  private fun tryLogin() {
    when (val description = this.viewModel.account.provider.authentication) {
      is AccountProviderAuthenticationDescription.SAML2_0 ->
        this.onTrySAML2Login(description)

      is AccountProviderAuthenticationDescription.OAuthWithIntermediary ->
        this.onTryOAuthLogin(description)

      is AccountProviderAuthenticationDescription.Basic ->
        this.onTryBasicLogin(description)

      is AccountProviderAuthenticationDescription.BasicToken ->
        this.onTryBasicTokenLogin(description)

      is AccountProviderAuthenticationDescription.Anonymous,
      is AccountProviderAuthenticationDescription.COPPAAgeGate ->
        throw UnreachableCodeException()
    }
  }

  /**
   * A click listener for the age checkbox. If the user wants to change their age, then
   * this must trigger an account logout.
   */

  private fun onAgeCheckboxClicked(): (View) -> Unit = {
    val isOver13 = this.viewModel.isOver13
    AlertDialog.Builder(this.requireContext())
      .setTitle(R.string.accountCOPPADeleteBooks)
      .setMessage(R.string.accountCOPPADeleteBooksConfirm)
      .setNegativeButton(R.string.accountCancel) { _, _ ->
        this.authenticationViews.setCOPPAState(
          isOver13 = isOver13,
          onAgeCheckboxClicked = this.onAgeCheckboxClicked()
        )
      }
      .setPositiveButton(R.string.accountDelete) { _, _ ->
        this.loginFormLock()
        this.viewModel.isOver13 = !isOver13
        this.viewModel.tryLogout()
      }
      .create()
      .show()
  }

  private fun isLocationPermissionGranted(): Boolean {
    return locationPermissions.all { permission ->
      ContextCompat.checkSelfPermission(requireContext(), permission) ==
        PackageManager.PERMISSION_GRANTED
    }
  }

  private fun shouldShowRationale(): Boolean {
    return locationPermissions.all { permission ->
      ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permission)
    }
  }

  private fun openAppSettings() {
    startActivity(
      Intent().apply {
        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        data = Uri.fromParts("package", requireContext().packageName, null)
      }
    )
  }

  private fun openCardCreatorWebView() {
    val cardCreatorURI = this.viewModel.account.provider.cardCreatorURI

    try {
      fusedLocationClient.lastLocation
        .addOnSuccessListener { location ->
          if (location != null) {
            listener.post(
              AccountDetailEvent.OpenWebView(
                AccountCardCreatorParameters(
                  url = cardCreatorURI.toString(),
                  lat = location.latitude,
                  long = location.longitude
                )
              )
            )
          } else {
            showErrorGettingLocationDialog()
          }
        }
        .addOnFailureListener {
          showErrorGettingLocationDialog()
        }
    } catch (exception: SecurityException) {
      this.logger.error("Error handling fusedLocationClient permissions")
    }
  }

  private fun showErrorGettingLocationDialog() {
    AlertDialog.Builder(requireContext())
      .setMessage(getString(R.string.accountCardCreatorLocationFailed))
      .create()
      .show()
  }

  private fun showLocationDisclaimerDialog() {
    AlertDialog.Builder(requireContext())
      .setMessage(R.string.accountCardCreatorDialogPermissionsMessage)
      .setPositiveButton(android.R.string.ok) { _, _ -> requestLocationPermissions() }
      .setNegativeButton(R.string.accountCardCreatorDialogCancel) { dialog, _ -> dialog?.dismiss() }
      .create()
      .show()
  }

  private fun showSettingsDialog() {
    AlertDialog.Builder(requireContext())
      .setMessage(R.string.accountCardCreatorDialogOpenSettingsMessage)
      .setPositiveButton(R.string.accountCardCreatorDialogOpenSettings) { _, _ ->
        openAppSettings()
      }
      .setNegativeButton(R.string.accountCardCreatorDialogCancel) { dialog, _ ->
        dialog.dismiss()
      }
      .create()
      .show()
  }

  private fun requestLocationPermissions() {
    locationPermissionCallback.launch(locationPermissions)
  }
}
