package org.nypl.simplified.ui.splash

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryRefresh
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.boot.api.BootEvent
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.accounts.AccountListRegistryViews
import org.nypl.simplified.ui.accounts.AccountProviderDescriptionListAdapter
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.main.MainApplication
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_NOT_CONSUMED
import org.nypl.simplified.ui.main.MainNotifications
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus.SPLASH_SCREEN_AWAITING_BOOT
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus.SPLASH_SCREEN_COMPLETED
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus.SPLASH_SCREEN_LIBRARY_SELECTOR
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus.SPLASH_SCREEN_NOTIFICATIONS
import org.slf4j.LoggerFactory

class SplashFragment :
  Fragment(),
  MainBackButtonConsumerType {
  private val logger =
    LoggerFactory.getLogger(SplashFragment::class.java)

  private lateinit var selectionListViewRoot: ViewGroup
  private lateinit var selectionListViews: LibrarySelectionViews
  private lateinit var splashNotificationsViewRoot: ViewGroup
  private lateinit var splashNotificationsViews: NotificationsViews
  private lateinit var splashHolder: ViewGroup
  private lateinit var splashViewRoot: ViewGroup
  private lateinit var splashViews: SplashViews
  private lateinit var accountListViews: AccountListRegistryViews

  private var subscriptions: CloseableCollectionType<ClosingResourceFailedException> =
    CloseableCollection.create()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    this.splashHolder =
      inflater.inflate(R.layout.splash_host, container, false) as ViewGroup

    this.splashViewRoot =
      inflater.inflate(R.layout.splash_boot, container, false) as ViewGroup
    this.splashViews =
      SplashViews(this.splashViewRoot)

    this.splashNotificationsViewRoot =
      inflater.inflate(R.layout.splash_notifications, container, false) as ViewGroup
    this.splashNotificationsViews =
      NotificationsViews(
        this.splashNotificationsViewRoot,
        onFinished = {
          val services =
            Services.serviceDirectory()
          val profiles =
            services.requireService(ProfilesControllerType::class.java)

          SplashModel.splashScreenCompleteNotifications(profiles)
        },
        onRequestPermission = {
          MainNotifications.requestPermissions(this.requireActivity())
        })

    this.selectionListViewRoot =
      inflater.inflate(R.layout.account_list_registry, container, false) as ViewGroup
    this.selectionListViews =
      LibrarySelectionViews(
        this.requireActivity(),
        this.selectionListViewRoot
      )

    this.openBootProgress()
    return this.splashHolder
  }

  private data class LibrarySelectionViews(
    private val activity: Activity,
    private val root: ViewGroup
  ) {
    val backButton: View =
      this.root.findViewById(R.id.accountRegistryToolbarBackIconTouch)

    init {
      this.backButton.visibility = View.GONE
    }
  }

  private data class NotificationsViews(
    private val root: ViewGroup,
    private val onFinished: () -> Unit,
    private val onRequestPermission: () -> Unit
  ) {
    val notificationsSkip =
      this.root.findViewById<View>(R.id.splashNotificationsSkip)
    val notificationsAllow =
      this.root.findViewById<View>(R.id.splashNotificationsAllow)

    init {
      this.notificationsSkip.setOnClickListener {
        this.onFinished.invoke()
      }
      this.notificationsAllow.setOnClickListener {
        this.onRequestPermission.invoke()
        this.onFinished.invoke()
      }
    }
  }

  private data class SplashViews(
    private val root: ViewGroup
  ) {
    val splashText: TextView =
      this.root.findViewById(R.id.splashText)
    val splashProgress: ProgressBar =
      this.root.findViewById(R.id.splashProgress)
    val splashVersion: TextView =
      this.root.findViewById(R.id.splashVersion)
    val splashImage: ImageView =
      this.root.findViewById(R.id.splashImage)
    val splashImageError: ImageView =
      this.root.findViewById(R.id.splashImageError)
    val splashSendError: Button =
      this.root.findViewById(R.id.splashSendError)
    val splashException: TextView =
      this.root.findViewById(R.id.splashException)
  }

  override fun onStart() {
    super.onStart()

    this.splashViews.splashImage.setImageResource(R.drawable.main_splash)
    this.splashViews.splashImageError.visibility = View.INVISIBLE
    this.splashViews.splashProgress.visibility = View.INVISIBLE
    this.splashViews.splashText.visibility = View.INVISIBLE
    this.splashViews.splashSendError.visibility = View.INVISIBLE
    this.splashViews.splashVersion.visibility = View.INVISIBLE

    /*
     * When someone clicks the splash image, it should vanish and show boot progress messages.
     * This is a debugging easter egg.
     */

    this.splashViews.splashImage.setOnClickListener {
      this.splashPopImageView()
    }

    /*
     * Subscribe to boot events so that we know when to progress through extra screens such
     * as onboarding, library selection, etc. The boot events observable will publish the most
     * recent event as soon as the subscription is created, so we don't need to do anything
     * other than subscribe in order to get the screen into the correct initial state.
     */

    this.subscriptions = CloseableCollection.create()
    this.subscriptions.add(
      MainApplication.application.servicesBootEvents.subscribe { _, newValue ->
        this.onBootEvent(newValue)
      }
    )

    this.subscriptions.add(
      SplashModel.splashScreenStatus.subscribe { _, newValue ->
        this.onSplashScreenStatusChanged(newValue)
      }
    )
  }

  private fun onSplashScreenStatusChanged(status: SplashModel.SplashScreenStatus) {
    when (status) {
      SPLASH_SCREEN_AWAITING_BOOT -> {
        this.openBootProgress()
      }

      SPLASH_SCREEN_NOTIFICATIONS -> {
        val services =
          Services.serviceDirectory()
        val profiles =
          services.requireService(ProfilesControllerType::class.java)

        if (SplashModel.userHasSeenNotifications(profiles)) {
          SplashModel.splashScreenCompleteNotifications(profiles)
        } else {
          this.openNotifications()
        }
      }

      SPLASH_SCREEN_LIBRARY_SELECTOR -> {
        val services =
          Services.serviceDirectory()
        val profiles =
          services.requireService(ProfilesControllerType::class.java)

        if (SplashModel.userHasSeenLibrarySelection(profiles)) {
          SplashModel.splashScreenCompleteLibrarySelection(profiles)
        } else {
          this.openLibrarySelection()
        }
      }

      SPLASH_SCREEN_COMPLETED -> {
        // Handled by the parent activity.
      }
    }
  }

  private fun openBootProgress() {
    this.splashHolder.removeAllViews()
    this.splashHolder.addView(this.splashViewRoot)
  }

  private fun openNotifications() {
    this.splashHolder.removeAllViews()
    this.splashHolder.addView(this.splashNotificationsViewRoot)
  }

  private fun onBootEvent(event: BootEvent) {
    UIThread.checkIsUIThread()

    return when (event) {
      is BootEvent.BootCompleted -> {
        SplashModel.splashScreenCompleteBoot()
      }

      is BootEvent.BootFailed -> {
        this.onBootFailed(event)
      }

      is BootEvent.BootInProgress -> {
        this.onBootInProgress(event)
      }
    }
  }

  private fun openLibrarySelection() {
    this.splashHolder.removeAllViews()
    this.splashHolder.addView(this.selectionListViewRoot)

    val services =
      Services.serviceDirectory()
    val imageLoader =
      services.requireService(ImageLoaderType::class.java)
    val registry =
      services.requireService(AccountProviderRegistryType::class.java)
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    registry.refreshAsync(
      AccountProviderRegistryRefresh.Incremental(
        includeTestingLibraries = false
      )
    )

    val accountListAdapter =
      AccountProviderDescriptionListAdapter(
        imageLoader = imageLoader,
        onItemClicked = { description ->
          this.onUserSelectedLibraryForAddition(description)
        }
      )

    this.accountListViews =
      AccountListRegistryViews.create(
        context = this.requireActivity(),
        rootView = this.selectionListViewRoot,
        accountListAdapter = accountListAdapter,
        onSwipeTouched = {
          registry.refreshAsync(
            AccountProviderRegistryRefresh.Incremental(
              includeTestingLibraries = profiles.profileCurrent().preferences().showTestingLibraries
            )
          )
        }
      )

    this.subscriptions.add(
      registry.accountProviderDescriptionsSortedAttribute.subscribe { _, descriptions ->
        this.accountListViews.submitAccountProviderDescriptionList(descriptions)
      }
    )
    this.subscriptions.add(
      registry.statusAttribute.subscribe { _, status ->
        this.accountListViews.reconfigureForRegistryStatus(status)
      }
    )
  }

  private fun onUserSelectedLibraryForAddition(description: AccountProviderDescription) {
    this.accountListViews.hideAccountList()

    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    // XXX: Should we wait to determine if the account was actually created?
    profiles.profileAccountCreate(description.id)

    SplashModel.splashScreenCompleteLibrarySelection(profiles)
  }

  private fun onBootInProgress(event: BootEvent.BootInProgress) {
    this.splashViews.splashText.text = event.message
  }

  private fun onBootFailed(event: BootEvent.BootFailed) {
    this.splashPopImageView()

    this.splashViews.splashException.visibility = View.VISIBLE
    this.splashViews.splashImage.visibility = View.INVISIBLE
    this.splashViews.splashImageError.visibility = View.VISIBLE
    this.splashViews.splashText.text = event.message
    this.splashViews.splashText.visibility = View.VISIBLE
    this.splashViews.splashVersion.visibility = View.VISIBLE
  }

  override fun onStop() {
    super.onStop()

    this.subscriptions.close()
  }

  private fun splashPopImageView() {
    this.splashViews.splashImage.animation =
      AnimationUtils.loadAnimation(this.context, R.anim.zoom_fade)
    this.splashViews.splashProgress.visibility = View.VISIBLE
    this.splashViews.splashText.visibility = View.VISIBLE
    this.splashViews.splashVersion.visibility = View.VISIBLE
  }

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result = BACK_BUTTON_NOT_CONSUMED
}
