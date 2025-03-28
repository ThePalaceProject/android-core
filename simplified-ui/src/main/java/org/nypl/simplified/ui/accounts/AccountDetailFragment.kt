package org.nypl.simplified.ui.accounts

import android.Manifest
import android.content.Context
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
import androidx.appcompat.widget.SwitchCompat
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.junreachable.UnimplementedCodeException
import com.io7m.junreachable.UnreachableCodeException
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
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
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.oauth.OAuthCallbackIntentParsing
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest.Basic
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest.BasicToken
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest.OAuthWithIntermediaryCancel
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest.OAuthWithIntermediaryInitiate
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus.AsCancelButtonDisabled
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus.AsCancelButtonEnabled
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus.AsLoginButtonDisabled
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus.AsLoginButtonEnabled
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus.AsLogoutButtonDisabled
import org.nypl.simplified.ui.accounts.AccountLoginButtonStatus.AsLogoutButtonEnabled
import org.nypl.simplified.ui.accounts.saml20.AccountSAML20Activity
import org.nypl.simplified.ui.accounts.saml20.AccountSAML20Model
import org.nypl.simplified.ui.images.ImageAccountIcons
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.main.MainApplication
import org.nypl.simplified.ui.main.MainNavigation
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI

/**
 * A fragment that shows settings for a single account.
 */

class AccountDetailFragment : Fragment(R.layout.account) {

  private lateinit var webViewDataDir: File

  private val logger =
    LoggerFactory.getLogger(AccountDetailFragment::class.java)

  private var subscriptions =
    CloseableCollection.create()

  private val fusedLocationClient by lazy {
    LocationServices.getFusedLocationProviderClient(this.requireActivity())
  }

  private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
  )

  private val locationPermissionCallback =
    this.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
      if (results.values.all { it }) {
        this.openCardCreatorWebView()
      } else if (!this.shouldShowRationale()) {
        this.showSettingsDialog()
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

  private val imageButtonLoadingTag = "IMAGE_BUTTON_LOADING"

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.accountEULA =
      view.findViewById(R.id.accountEULA)
    this.accountTitle =
      view.findViewById(R.id.accountCellTitle)
    this.accountSubtitle =
      view.findViewById(R.id.accountCellSubtitle)
    this.accountIcon =
      view.findViewById(R.id.accountCellIcon)

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

    if (AccountDetailModel.showPleaseLoginTitle) {
      this.loginTitle.visibility = View.VISIBLE
    } else {
      this.loginTitle.visibility = View.GONE
    }

    /*
     * Instantiate views for alternative authentication methods.
     */

    this.authenticationAlternativesMake()
  }

  private fun onBasicUserPasswordChanged(
    username: AccountUsername,
    password: AccountPassword
  ) {
    this.setLoginButtonStatus(this.determineLoginIsSatisfied())
  }

  private fun determineLoginIsSatisfied(): AccountLoginButtonStatus {
    val authDescription = AccountDetailModel.account.provider.authentication
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
    return AccountDetailModel.account.provider.cardCreatorURI != null
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions =
      CloseableCollection.create()

    this.webViewDataDir =
      this.requireContext().getDir("webview", Context.MODE_PRIVATE)

    val imageLoader =
      Services.serviceDirectory()
        .requireService(ImageLoaderType::class.java)

    ImageAccountIcons.loadAccountLogoIntoView(
      imageLoader.loader,
      AccountDetailModel.account.provider.toDescription(),
      R.drawable.account_default,
      this.accountIcon
    )

    /*
     * Launch Card Creator
     */

    this.signUpButton.setOnClickListener {
      if (!this.isLocationPermissionGranted()) {
        if (this.shouldShowRationale()) {
          this.showLocationDisclaimerDialog()
        } else {
          this.requestLocationPermissions()
        }
      } else {
        this.openCardCreatorWebView()
      }
    }

    /*
     * Configure the bookmark syncing switch to enable/disable syncing permissions.
     */

    this.bookmarkSyncCheck.setOnClickListener {
      AccountDetailModel.enableBookmarkSyncing(this.bookmarkSyncCheck.isChecked)
    }

    /*
     * Configure the "Report issue..." item.
     */

    this.configureReportIssue()

    /*
     * Populate the barcode if passed in (e.g. via deep link).
     */

    val barcode = AccountDetailModel.barcode
    if (barcode == null) {
      this.authenticationViews.blank()
    } else {
      when (AccountDetailModel.account.provider.authentication) {
        is AccountProviderAuthenticationDescription.Basic -> {
          this.authenticationViews.setBasicUserAndPass(
            user = barcode,
            password = ""
          )
        }

        is AccountProviderAuthenticationDescription.BasicToken -> {
          this.authenticationViews.setBasicTokenUserAndPass(
            user = barcode,
            password = ""
          )
        }

        AccountProviderAuthenticationDescription.Anonymous,
        is AccountProviderAuthenticationDescription.OAuthWithIntermediary,
        is AccountProviderAuthenticationDescription.SAML2_0 -> {
          // Nothing to do.
        }
      }
    }

    /*
     * Eagerly reconfigure the UI to ensure an up-to-date view when resuming from sleep.
     */

    this.reconfigureAccountUI()
  }

  private fun instantiateAlternativeAuthenticationViews() {
    val account = AccountDetailModel.account
    for (alternative in account.provider.authenticationAlternatives) {
      when (alternative) {
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
    val supportUrl = AccountDetailModel.account.provider.supportEmail
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
            this.logger.debug("unable to start activity: ", e)
            val context = this.requireContext()
            MaterialAlertDialogBuilder(context)
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
    AccountDetailModel.tryLogin(
      ProfileAccountLoginRequest.SAML20Initiate(
        accountId = AccountDetailModel.account.id,
        description = authenticationDescription
      )
    )

    AccountSAML20Model.startAuthenticationProcess(
      application = MainApplication.application,
      accountID = AccountDetailModel.account.id,
      authenticationDescription = authenticationDescription,
      webViewDataDirectory = this.webViewDataDir
    )

    val intent = Intent(this.requireContext(), AccountSAML20Activity::class.java)
    this.requireActivity().startActivity(intent)
  }

  private fun onTryOAuthLogin(
    authenticationDescription: AccountProviderAuthenticationDescription.OAuthWithIntermediary
  ) {
    AccountDetailModel.tryLogin(
      OAuthWithIntermediaryInitiate(
        accountId = AccountDetailModel.account.id,
        description = authenticationDescription
      )
    )
    this.sendOAuthIntent(authenticationDescription)
  }

  private fun onTryBasicLogin(
    description: AccountProviderAuthenticationDescription.Basic
  ) {
    val accountPassword: AccountPassword =
      this.authenticationViews.getBasicPassword()
    val accountUsername: AccountUsername =
      this.authenticationViews.getBasicUser()

    val request =
      Basic(
        accountId = AccountDetailModel.account.id,
        description = description,
        password = accountPassword,
        username = accountUsername
      )

    AccountDetailModel.tryLogin(request)
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
        accountId = AccountDetailModel.account.id,
        description = description,
        password = accountPassword,
        username = accountUsername
      )

    AccountDetailModel.tryLogin(request)
  }

  private fun sendOAuthIntent(
    authenticationDescription: AccountProviderAuthenticationDescription.OAuthWithIntermediary
  ) {
    val services =
      Services.serviceDirectory()
    val buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)
    val callbackScheme =
      buildConfig.oauthCallbackScheme.scheme
    val callbackUrl =
      OAuthCallbackIntentParsing.createUri(
        requiredScheme = callbackScheme,
        accountId = AccountDetailModel.account.id.uuid
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

  override fun onStop() {
    super.onStop()

    this.subscriptions.close()
  }

  private fun reconfigureAccountUI() {
    val account = AccountDetailModel.account

    this.authenticationViews.showFor(account.provider.authentication)

    if (account.provider.cardCreatorURI != null) {
      this.settingsCardCreator.visibility = View.VISIBLE
    }

    this.accountTitle.text =
      account.provider.displayName
    this.accountSubtitle.text =
      account.provider.subtitle

    this.bookmarkSync.visibility = if (account.requiresCredentials) {
      View.VISIBLE
    } else {
      View.GONE
    }

    this.bookmarkSyncCheck.isEnabled = account.isBookmarkSyncable()
    this.bookmarkSyncCheck.isChecked = account.preferences.bookmarkSyncingPermitted

    /*
     * Only show a EULA if there's actually a EULA.
     */

    val eula = account.provider.eula
    if (eula != null) {
      this.accountEULA.visibility = View.VISIBLE
      this.accountEULA.setOnClickListener {
        MainNavigation.Settings.openDocument(
          this.getString(R.string.accountEULA),
          eula.toURL()
        )
      }
    } else {
      this.accountEULA.visibility = View.GONE
    }

    val privacyPolicy = account.provider.privacyPolicy
    if (privacyPolicy != null) {
      this.accountPrivacyPolicy.visibility = View.VISIBLE
      this.accountPrivacyPolicy.setOnClickListener {
        MainNavigation.Settings.openDocument(
          this.getString(R.string.accountPrivacyPolicy),
          privacyPolicy.toURL()
        )
      }
    } else {
      this.accountPrivacyPolicy.visibility = View.GONE
    }

    val licenses = account.provider.license
    if (licenses != null) {
      this.accountLicenses.visibility = View.VISIBLE
      this.accountLicenses.setOnClickListener {
        MainNavigation.Settings.openDocument(
          this.getString(R.string.accountLicenses),
          licenses.toURL()
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

    val catalogURIOverride = account.preferences.catalogURIOverride
    this.accountCustomOPDSField.text = catalogURIOverride?.toString() ?: ""
    this.accountCustomOPDS.visibility =
      if (catalogURIOverride != null) {
        View.VISIBLE
      } else {
        View.GONE
      }

    this.disableSyncSwitchForLoginState(account.loginState)

    return when (val loginState = account.loginState) {
      AccountNotLoggedIn -> {
        this.loginProgress.visibility = View.GONE
        this.setLoginButtonStatus(
          AsLoginButtonEnabled {
            this.loginFormLock()
            this.tryLogin()
          }
        )

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
            when (val desc = loginState.description) {
              AccountProviderAuthenticationDescription.Anonymous,
              is AccountProviderAuthenticationDescription.Basic,
              is AccountProviderAuthenticationDescription.BasicToken -> {
                throw UnreachableCodeException()
              }

              is AccountProviderAuthenticationDescription.OAuthWithIntermediary -> {
                AccountDetailModel.tryLogin(
                  OAuthWithIntermediaryCancel(
                    accountId = account.id,
                    description = desc
                  )
                )
              }

              is AccountProviderAuthenticationDescription.SAML2_0 -> {
                AccountDetailModel.tryLogin(
                  ProfileAccountLoginRequest.SAML20Cancel(
                    accountId = account.id,
                    description = desc
                  )
                )
              }
            }
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
          AccountDetailModel.openErrorPage(loginState.taskResult.steps)
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
            AccountDetailModel.tryLogout()
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
            AccountDetailModel.tryLogout()
          }
        )

        this.loginButtonErrorDetails.visibility = View.VISIBLE
        this.loginButtonErrorDetails.setOnClickListener {
          AccountDetailModel.openErrorPage(loginState.taskResult.steps)
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
    val services =
      Services.serviceDirectory()
    val imageLoader =
      services.requireService(ImageLoaderType::class.java)

    imageLoader.loader.cancelTag(this.imageButtonLoadingTag)
  }

  private fun setLoginButtonStatus(
    status: AccountLoginButtonStatus
  ) {
    this.authenticationViews.setLoginButtonStatus(status)

    val account =
      AccountDetailModel.account
    val supportUrl =
      account.provider.supportEmail
    val resetPasswordURI =
      account.provider.resetPasswordURI

    this.authenticationViews.setResetPasswordLabelStatus(
      status,
      isVisible = resetPasswordURI != null,
      onClick = {
        try {
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resetPasswordURI.toString()))
          this.startActivity(intent)
        } catch (e: Exception) {
          this.logger.debug("unable to start activity: ", e)
          val context = this.requireContext()
          MaterialAlertDialogBuilder(context)
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
        val enableSignup = this.shouldSignUpBeEnabled()
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
      val services =
        Services.serviceDirectory()
      val imageLoader =
        services.requireService(ImageLoaderType::class.java)

      view.setImageDrawable(null)
      view.visibility = View.VISIBLE
      imageLoader.loader.load(uri.toString())
        .fit()
        .tag(this.imageButtonLoadingTag)
        .into(
          view,
          object : com.squareup.picasso.Callback {
            override fun onSuccess() {
              onSuccess.invoke()
            }

            override fun onError(e: Exception) {
              this@AccountDetailFragment.logger.debug("failed to load authentication logo: ", e)
              view.visibility = View.GONE
            }
          }
        )
    }
  }

  private fun loginFormLock() {
    this.authenticationViews.lock()

    this.setLoginButtonStatus(AsLoginButtonDisabled)
    this.authenticationAlternativesHide()
  }

  private fun loginFormUnlock() {
    this.authenticationViews.unlock()

    val loginSatisfied = this.determineLoginIsSatisfied()
    this.setLoginButtonStatus(loginSatisfied)
    this.authenticationAlternativesShow()
    if (this.shouldSignUpBeEnabled()) {
      this.signUpButton.isEnabled = true
      this.signUpLabel.isEnabled = true
    }
  }

  private fun authenticationAlternativesMake() {
    val account = AccountDetailModel.account
    this.authenticationAlternativesButtons.removeAllViews()
    if (account.provider.authenticationAlternatives.isEmpty()) {
      this.authenticationAlternativesHide()
    } else {
      this.instantiateAlternativeAuthenticationViews()
      this.authenticationAlternativesShow()
    }
  }

  private fun authenticationAlternativesShow() {
    val account = AccountDetailModel.account
    if (account.provider.authenticationAlternatives.isNotEmpty()) {
      this.authenticationAlternatives.visibility = View.VISIBLE
    }
  }

  private fun authenticationAlternativesHide() {
    this.authenticationAlternatives.visibility = View.GONE
  }

  private fun tryLogin() {
    val account = AccountDetailModel.account
    when (val description = account.provider.authentication) {
      is AccountProviderAuthenticationDescription.SAML2_0 ->
        this.onTrySAML2Login(description)

      is AccountProviderAuthenticationDescription.OAuthWithIntermediary ->
        this.onTryOAuthLogin(description)

      is AccountProviderAuthenticationDescription.Basic ->
        this.onTryBasicLogin(description)

      is AccountProviderAuthenticationDescription.BasicToken ->
        this.onTryBasicTokenLogin(description)

      is AccountProviderAuthenticationDescription.Anonymous ->
        throw UnreachableCodeException()
    }
  }

  private fun isLocationPermissionGranted(): Boolean {
    return this.locationPermissions.all { permission ->
      ContextCompat.checkSelfPermission(this.requireContext(), permission) ==
        PackageManager.PERMISSION_GRANTED
    }
  }

  private fun shouldShowRationale(): Boolean {
    return this.locationPermissions.all { permission ->
      ActivityCompat.shouldShowRequestPermissionRationale(this.requireActivity(), permission)
    }
  }

  private fun openAppSettings() {
    this.startActivity(
      Intent().apply {
        this.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        this.data =
          Uri.fromParts("package", this@AccountDetailFragment.requireContext().packageName, null)
      }
    )
  }

  private fun openCardCreatorWebView() {
    val account =
      AccountDetailModel.account
    val cardCreatorURI =
      account.provider.cardCreatorURI

    try {
      this.fusedLocationClient.lastLocation
        .addOnSuccessListener { location ->
          if (location != null) {
            MainNavigation.Settings.openCardCreator(
              AccountCardCreatorParameters(
                url = cardCreatorURI.toString(),
                lat = location.latitude,
                long = location.longitude
              )
            )
          } else {
            this.showErrorGettingLocationDialog()
          }
        }
        .addOnFailureListener {
          this.showErrorGettingLocationDialog()
        }
    } catch (exception: SecurityException) {
      this.logger.error("Error handling fusedLocationClient permissions")
    }
  }

  private fun showErrorGettingLocationDialog() {
    MaterialAlertDialogBuilder(this.requireContext())
      .setMessage(this.getString(R.string.accountCardCreatorLocationFailed))
      .create()
      .show()
  }

  private fun showLocationDisclaimerDialog() {
    MaterialAlertDialogBuilder(this.requireContext())
      .setMessage(R.string.accountCardCreatorDialogPermissionsMessage)
      .setPositiveButton(android.R.string.ok) { _, _ -> this.requestLocationPermissions() }
      .setNegativeButton(R.string.accountCardCreatorDialogCancel) { dialog, _ -> dialog?.dismiss() }
      .create()
      .show()
  }

  private fun showSettingsDialog() {
    MaterialAlertDialogBuilder(this.requireContext())
      .setMessage(R.string.accountCardCreatorDialogOpenSettingsMessage)
      .setPositiveButton(R.string.accountCardCreatorDialogOpenSettings) { _, _ ->
        this.openAppSettings()
      }
      .setNegativeButton(R.string.accountCardCreatorDialogCancel) { dialog, _ ->
        dialog.dismiss()
      }
      .create()
      .show()
  }

  private fun requestLocationPermissions() {
    this.locationPermissionCallback.launch(this.locationPermissions)
  }
}
