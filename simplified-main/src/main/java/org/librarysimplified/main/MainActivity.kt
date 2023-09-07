package org.librarysimplified.main

import android.app.ActionBar
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.TxContextWrappingDelegate
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import com.google.firebase.dynamiclinks.PendingDynamicLinkData
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.onboarding.OnboardingEvent
import org.librarysimplified.ui.onboarding.OnboardingFragment
import org.librarysimplified.ui.splash.SplashEvent
import org.librarysimplified.ui.splash.SplashFragment
import org.librarysimplified.ui.tutorial.TutorialEvent
import org.librarysimplified.ui.tutorial.TutorialFragment
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.deeplinks.controller.api.DeepLinksControllerType
import org.nypl.simplified.deeplinks.controller.api.ScreenID
import org.nypl.simplified.listeners.api.ListenerRepository
import org.nypl.simplified.listeners.api.listenerRepositories
import org.nypl.simplified.oauth.OAuthCallbackIntentParsing
import org.nypl.simplified.oauth.OAuthParseResult
import org.nypl.simplified.profiles.api.ProfilesDatabaseType.AnonymousProfileEnabled.ANONYMOUS_PROFILE_DISABLED
import org.nypl.simplified.profiles.api.ProfilesDatabaseType.AnonymousProfileEnabled.ANONYMOUS_PROFILE_ENABLED
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest.OAuthWithIntermediaryComplete
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.ui.branding.BrandingSplashServiceType
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.ServiceLoader
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(R.layout.main_host) {

  companion object {
    private const val STATE_ACTION_BAR_IS_SHOWING = "ACTION_BAR_IS_SHOWING"
    private const val LOGIN_SCREEN_ID = "login"
  }

  private val logger = LoggerFactory.getLogger(MainActivity::class.java)
  private val listenerRepo: ListenerRepository<MainActivityListenedEvent, Unit> by listenerRepositories()

  private val appCompatDelegate: TxContextWrappingDelegate by lazy {
    TxContextWrappingDelegate(super.getDelegate())
  }

  private val defaultViewModelFactory: ViewModelProvider.Factory by lazy {
    MainActivityDefaultViewModelFactory(super.defaultViewModelProviderFactory)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    this.logger.debug("onCreate (recreating {})", savedInstanceState != null)
    super.onCreate(savedInstanceState)
    this.logger.debug("onCreate (super completed)")

    interceptDeepLink()
    val toolbar = this.findViewById(R.id.mainToolbar) as Toolbar
    this.setSupportActionBar(toolbar)
    this.supportActionBar?.setDisplayHomeAsUpEnabled(true)
    this.supportActionBar?.setDisplayShowHomeEnabled(true)
    this.supportActionBar?.hide() // Hide toolbar until requested

    if (savedInstanceState == null) {
      this.openSplashScreen()
    } else {
      if (savedInstanceState.getBoolean(STATE_ACTION_BAR_IS_SHOWING)) {
        this.supportActionBar?.show()
      } else {
        this.supportActionBar?.hide()
      }
    }
  }

  private fun interceptDeepLink() {
    val pendingLink =
      FirebaseDynamicLinks.getInstance()
        .getDynamicLink(intent)

    pendingLink.addOnFailureListener(this) { e ->
      this.logger.error("Failed to retrieve dynamic link: ", e)
    }

    pendingLink.addOnSuccessListener { linkData: PendingDynamicLinkData? ->
      val deepLink = linkData?.link
      if (deepLink == null) {
        this.logger.error("Pending deep link had no link field")
        return@addOnSuccessListener
      }

      val libraryID = deepLink.getQueryParameter("libraryid")
      if (libraryID == null) {
        this.logger.error("Pending deep link had no libraryid parameter.")
        return@addOnSuccessListener
      }

      val barcode = deepLink.getQueryParameter("barcode")
      if (barcode == null) {
        this.logger.error("Pending deep link had no barcode parameter.")
        return@addOnSuccessListener
      }

      val services =
        Services.serviceDirectory()
      val profiles =
        services.requireService(ProfilesControllerType::class.java)
      val deepLinksController =
        services.requireService(DeepLinksControllerType::class.java)

      val accountURI =
        URI("urn:uuid" + libraryID)

      val accountResult =
        profiles.profileAccountCreate(accountURI)
          .get(3L, TimeUnit.MINUTES)

      // XXX: Creating an error report would be nice here.
      if (accountResult is TaskResult.Failure) {
        this.logger.error("Unable to create an account with ID {}: ", accountURI)
        return@addOnSuccessListener
      }

      val accountID =
        (accountResult as TaskResult.Success).result.id
      val screenRaw =
        deepLink.getQueryParameter("screen")

      val screenId: ScreenID =
        when (screenRaw) {
          null -> {
            this.logger.warn("Deep link did not have a screen parameter.")
            ScreenID.UNSPECIFIED
          }
          LOGIN_SCREEN_ID -> ScreenID.LOGIN
          else -> {
            this.logger.warn("Deep link had an unrecognized screen parameter {}.", screenRaw)
            ScreenID.UNSPECIFIED
          }
        }

      deepLinksController.publishDeepLinkEvent(
        accountID = accountID,
        screenID = screenId,
        barcode = barcode
      )
    }
  }

  override val defaultViewModelProviderFactory: ViewModelProvider.Factory
    get() = this.defaultViewModelFactory

  override fun getDelegate(): AppCompatDelegate {
    return this.appCompatDelegate
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(STATE_ACTION_BAR_IS_SHOWING, this.supportActionBar?.isShowing ?: false)
  }

  override fun getActionBar(): ActionBar? {
    this.logger.warn("Use 'getSupportActionBar' instead")
    return super.getActionBar()
  }

  override fun onNewIntent(intent: Intent) {
    if (Services.isInitialized()) {
      if (this.tryToCompleteOAuthIntent(intent)) {
        return
      }
    }
    super.onNewIntent(intent)
  }

  private fun tryToCompleteOAuthIntent(
    intent: Intent
  ): Boolean {
    this.logger.debug("attempting to parse incoming intent as OAuth token")

    val buildConfiguration =
      Services.serviceDirectory()
        .requireService(BuildConfigurationServiceType::class.java)

    val result = OAuthCallbackIntentParsing.processIntent(
      intent = intent,
      requiredScheme = buildConfiguration.oauthCallbackScheme.scheme,
      parseUri = Uri::parse
    )

    if (result is OAuthParseResult.Failed) {
      this.logger.warn("failed to parse incoming intent: {}", result.message)
      return false
    }

    this.logger.debug("parsed OAuth token")
    val accountId = AccountID((result as OAuthParseResult.Success).accountId)
    val token = result.token

    val profilesController =
      Services.serviceDirectory()
        .requireService(ProfilesControllerType::class.java)

    profilesController.profileAccountLogin(
      OAuthWithIntermediaryComplete(
        accountId = accountId,
        token = token
      )
    )
    return true
  }

  override fun onUserInteraction() {
    super.onUserInteraction()

    /*
     * Each time the user interacts with something onscreen, reset the timer.
     */

    if (Services.isInitialized()) {
      Services.serviceDirectory()
        .requireService(ProfilesControllerType::class.java)
        .profileIdleTimer()
        .reset()
    }
  }

  override fun onStart() {
    super.onStart()
    this.listenerRepo.registerHandler(this::handleEvent)
    interceptDeepLink()
  }

  override fun onStop() {
    super.onStop()
    this.listenerRepo.unregisterHandler()
  }

  @Suppress("UNUSED_PARAMETER")
  private fun handleEvent(event: MainActivityListenedEvent, state: Unit) {
    return when (event) {
      is MainActivityListenedEvent.SplashEvent ->
        this.handleSplashEvent(event.event)

      is MainActivityListenedEvent.TutorialEvent ->
        this.handleTutorialEvent(event.event)

      is MainActivityListenedEvent.OnboardingEvent ->
        this.handleOnboardingEvent(event.event)
    }
  }

  private fun handleSplashEvent(event: SplashEvent) {
    return when (event) {
      SplashEvent.SplashCompleted ->
        this.onSplashFinished()
    }
  }

  private fun onSplashFinished() {
    this.logger.debug("onSplashFinished")

    val appCache =
      AppCache(this)

    if (appCache.isTutorialSeen()) {
      this.onTutorialFinished()
    } else {
      this.openTutorial()
      appCache.setTutorialSeen(true)
    }
  }

  private fun handleTutorialEvent(event: TutorialEvent) {
    return when (event) {
      TutorialEvent.TutorialCompleted ->
        this.onTutorialFinished()
    }
  }

  private fun onTutorialFinished() {
    this.logger.debug("onTutorialFinished")

    val services =
      Services.serviceDirectory()
    val profilesController =
      services.requireService(ProfilesControllerType::class.java)
    val accountProviders =
      services.requireService(AccountProviderRegistryType::class.java)
    val splashService = getSplashService()

    when (profilesController.profileAnonymousEnabled()) {
      ANONYMOUS_PROFILE_ENABLED -> {
        val profile = profilesController.profileCurrent()
        val defaultProvider = accountProviders.defaultProvider

        val hasNonDefaultAccount =
          profile.accounts().values.count { it.provider.id != defaultProvider.id } > 0
        this.logger.debug("hasNonDefaultAccount=$hasNonDefaultAccount")

        val shouldShowLibrarySelectionScreen =
          splashService.shouldShowLibrarySelectionScreen && !profile.preferences().hasSeenLibrarySelectionScreen
        this.logger.debug("shouldShowLibrarySelectionScreen=$shouldShowLibrarySelectionScreen")

        if (!hasNonDefaultAccount && shouldShowLibrarySelectionScreen) {
          this.openOnboarding()
        } else {
          this.onOnboardingFinished()
        }
      }

      ANONYMOUS_PROFILE_DISABLED -> {
        // Not used anymore.
      }
    }
  }

  private fun handleOnboardingEvent(event: OnboardingEvent) {
    return when (event) {
      OnboardingEvent.OnboardingCompleted ->
        this.onOnboardingFinished()
    }
  }

  private fun onOnboardingFinished() {
    this.openMainFragment()
  }

  private fun openMainBackStack() {
    this.logger.debug("openMain")
    val mainFragment = MainFragment()
    this.supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.mainFragmentHolder, mainFragment, "MAIN")
      .addToBackStack(null)
      .commit()
  }

  private fun getSplashService(): BrandingSplashServiceType {
    return ServiceLoader
      .load(BrandingSplashServiceType::class.java)
      .firstOrNull()
      ?: throw IllegalStateException(
        "No available services of type ${BrandingSplashServiceType::class.java.canonicalName}"
      )
  }

  private fun openSplashScreen() {
    this.logger.debug("openSplashScreen")
    val splashFragment = SplashFragment()
    this.supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    this.supportFragmentManager.commit {
      replace(R.id.mainFragmentHolder, splashFragment, "SPLASH_MAIN")
    }
  }

  private fun openTutorial() {
    this.logger.debug("openTutorial")
    val tutorialFragment = TutorialFragment()
    this.supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    this.supportFragmentManager.commit {
      replace(R.id.mainFragmentHolder, tutorialFragment)
    }
  }

  private fun openOnboarding() {
    this.logger.debug("openOnboarding")
    val onboardingFragment = OnboardingFragment()
    this.supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    this.supportFragmentManager.commit {
      replace(R.id.mainFragmentHolder, onboardingFragment)
    }
  }

  private fun openMainFragment() {
    this.logger.debug("openMainFragment")
    val mainFragment = MainFragment()
    this.supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    this.supportFragmentManager.commit {
      replace(R.id.mainFragmentHolder, mainFragment, "MAIN")
    }
  }
}
