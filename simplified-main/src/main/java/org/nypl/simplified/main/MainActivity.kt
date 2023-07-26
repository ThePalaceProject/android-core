package org.nypl.simplified.main

import android.app.ActionBar
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.deeplinks.controller.api.DeepLinksControllerType
import org.nypl.simplified.deeplinks.controller.api.ScreenID
import org.nypl.simplified.listeners.api.ListenerRepository
import org.nypl.simplified.listeners.api.listenerRepositories
import org.nypl.simplified.oauth.OAuthCallbackIntentParsing
import org.nypl.simplified.oauth.OAuthParseResult
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfilesDatabaseType.AnonymousProfileEnabled.ANONYMOUS_PROFILE_DISABLED
import org.nypl.simplified.profiles.api.ProfilesDatabaseType.AnonymousProfileEnabled.ANONYMOUS_PROFILE_ENABLED
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest.OAuthWithIntermediaryComplete
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.ui.branding.BrandingSplashServiceType
import org.nypl.simplified.ui.onboarding.OnboardingEvent
import org.nypl.simplified.ui.onboarding.OnboardingFragment
import org.nypl.simplified.ui.profiles.ProfileModificationDefaultFragment
import org.nypl.simplified.ui.profiles.ProfileModificationEvent
import org.nypl.simplified.ui.profiles.ProfileModificationFragmentParameters
import org.nypl.simplified.ui.profiles.ProfileModificationFragmentServiceType
import org.nypl.simplified.ui.profiles.ProfileSelectionEvent
import org.nypl.simplified.ui.profiles.ProfileSelectionFragment
import org.nypl.simplified.ui.splash.SplashEvent
import org.nypl.simplified.ui.splash.SplashFragment
import org.nypl.simplified.ui.tutorial.TutorialEvent
import org.nypl.simplified.ui.tutorial.TutorialFragment
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

  private val defaultViewModelFactory: ViewModelProvider.Factory by lazy {
    MainActivityDefaultViewModelFactory(super.getDefaultViewModelProviderFactory())
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

    pendingLink.addOnSuccessListener { linkData ->
      val deepLink = linkData.link
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

  override fun getDefaultViewModelProviderFactory(): ViewModelProvider.Factory {
    return this.defaultViewModelFactory
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

      is MainActivityListenedEvent.MainFragmentEvent ->
        this.handleMainFragmentEvent(event.event)

      is MainActivityListenedEvent.ProfileSelectionEvent ->
        this.handleProfileSelectionEvent(event.event)

      is MainActivityListenedEvent.ProfileModificationEvent ->
        this.handleProfileModificationEvent(event.event)
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
        this.openProfileSelection()
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

  private fun handleMainFragmentEvent(event: MainFragmentEvent) {
    return when (event) {
      MainFragmentEvent.ProfileIdleTimedOut, MainFragmentEvent.SwitchProfileSelected ->
        this.openProfileSelect()
    }
  }

  private fun handleProfileSelectionEvent(event: ProfileSelectionEvent) {
    return when (event) {
      ProfileSelectionEvent.OpenProfileCreation ->
        this.openProfileCreate()

      is ProfileSelectionEvent.OpenProfileModification ->
        this.openProfileModify(event.profile)

      ProfileSelectionEvent.ProfileSelected ->
        this.openMainBackStack()
    }
  }

  private fun handleProfileModificationEvent(event: ProfileModificationEvent) {
    return when (event) {
      ProfileModificationEvent.Cancelled ->
        this.onProfileModificationCancelled()

      ProfileModificationEvent.Succeeded ->
        this.onProfileModificationSucceeded()
    }
  }

  private fun openModificationFragment(
    parameters: ProfileModificationFragmentParameters
  ) {
    val fragmentService =
      Services.serviceDirectory()
        .optionalService(ProfileModificationFragmentServiceType::class.java)

    val fragment =
      if (fragmentService != null) {
        this.logger.debug("found a profile modification fragment service: {}", fragmentService)
        fragmentService.createModificationFragment(parameters)
      } else {
        ProfileModificationDefaultFragment.create(parameters)
      }

    this.supportFragmentManager.beginTransaction()
      .replace(R.id.mainFragmentHolder, fragment, "MAIN")
      .addToBackStack(null)
      .commit()
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

  private fun openProfileSelect() {
    this.logger.debug("openProfileSelect")
    val newFragment = ProfileSelectionFragment()
    this.supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.mainFragmentHolder, newFragment, "MAIN")
      .commit()
  }

  private fun openProfileModify(id: ProfileID) {
    this.logger.debug("openProfileModify: ${id.uuid}")
    this.openModificationFragment(ProfileModificationFragmentParameters(id))
  }

  private fun openProfileCreate() {
    this.logger.debug("openProfileCreate")
    this.openModificationFragment(ProfileModificationFragmentParameters(null))
  }

  private fun onProfileModificationSucceeded() {
    this.supportFragmentManager.popBackStack()
  }

  private fun onProfileModificationCancelled() {
    this.supportFragmentManager.popBackStack()
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

  private fun openProfileSelection() {
    this.logger.debug("openProfileSelection")
    val profilesFragment = ProfileSelectionFragment()
    this.supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    this.supportFragmentManager.commit {
      replace(R.id.mainFragmentHolder, profilesFragment)
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
