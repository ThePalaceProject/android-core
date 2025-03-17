package org.librarysimplified.main

import android.Manifest
import android.app.ActionBar
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.TxContextWrappingDelegate2
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
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
import org.nypl.simplified.listeners.api.ListenerRepository
import org.nypl.simplified.listeners.api.listenerRepositories
import org.nypl.simplified.oauth.OAuthCallbackIntentParsing
import org.nypl.simplified.oauth.OAuthParseResult
import org.nypl.simplified.profiles.api.ProfilesDatabaseType.AnonymousProfileEnabled.ANONYMOUS_PROFILE_DISABLED
import org.nypl.simplified.profiles.api.ProfilesDatabaseType.AnonymousProfileEnabled.ANONYMOUS_PROFILE_ENABLED
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest.OAuthWithIntermediaryComplete
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.branding.BrandingSplashServiceType
import org.slf4j.LoggerFactory
import java.util.ServiceLoader

class MainActivity : AppCompatActivity(R.layout.main_host) {

  companion object {
    private const val STATE_ACTION_BAR_IS_SHOWING = "ACTION_BAR_IS_SHOWING"
    private const val LOGIN_SCREEN_ID = "login"
  }

  private val logger = LoggerFactory.getLogger(MainActivity::class.java)
  private val listenerRepo: ListenerRepository<MainActivityListenedEvent, Unit> by listenerRepositories()

  private val appCompatDelegate: TxContextWrappingDelegate2 by lazy {
    TxContextWrappingDelegate2(super.getDelegate())
  }

  private val defaultViewModelFactory: ViewModelProvider.Factory by lazy {
    MainActivityDefaultViewModelFactory(super.defaultViewModelProviderFactory)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    this.logger.debug("onCreate (recreating {})", savedInstanceState != null)
    super.onCreate(savedInstanceState)
    this.logger.debug("onCreate (super completed)")

    if (savedInstanceState == null) {
      this.openSplashScreen()
    }

    this.askForNotificationsPermission()
  }

  private fun askForNotificationsPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
        100
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

  override fun onStart() {
    super.onStart()
    this.listenerRepo.registerHandler(this::handleEvent)
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
