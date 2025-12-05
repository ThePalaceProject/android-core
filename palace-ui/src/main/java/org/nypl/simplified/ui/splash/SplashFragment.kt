package org.nypl.simplified.ui.splash

import android.app.Activity
import android.graphics.Matrix
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.Group
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.io7m.jattribute.core.AttributeType
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus.Idle
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus.Refreshing
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.boot.api.BootEvent
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.accounts.FilterableAccountListAdapter
import org.nypl.simplified.ui.accounts.SpaceItemDecoration
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.main.MainApplication
import org.nypl.simplified.ui.main.MainAttributes
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_NOT_CONSUMED
import org.nypl.simplified.ui.main.MainNotifications
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus.SPLASH_SCREEN_AWAITING_BOOT
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus.SPLASH_SCREEN_COMPLETED
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus.SPLASH_SCREEN_LIBRARY_SELECTOR
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus.SPLASH_SCREEN_NOTIFICATIONS
import org.nypl.simplified.ui.splash.SplashModel.SplashScreenStatus.SPLASH_SCREEN_TUTORIAL
import org.slf4j.LoggerFactory

class SplashFragment : Fragment(), MainBackButtonConsumerType {

  private val logger =
    LoggerFactory.getLogger(SplashFragment::class.java)

  private lateinit var selectionListViewRoot: ViewGroup
  private lateinit var selectionListViews: LibrarySelectionViews
  private lateinit var splashNotificationsViewRoot: ViewGroup
  private lateinit var splashNotificationsViews: NotificationsViews
  private lateinit var splashHolder: ViewGroup
  private lateinit var splashViewRoot: ViewGroup
  private lateinit var splashViews: SplashViews
  private lateinit var tutorialViewRoot: ViewGroup
  private lateinit var tutorialViews: TutorialViews

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

    this.tutorialViewRoot =
      inflater.inflate(R.layout.splash_tutorial, container, false) as ViewGroup
    this.tutorialViews =
      TutorialViews(this.tutorialViewRoot) {
        val services =
          Services.serviceDirectory()
        val profiles =
          services.requireService(ProfilesControllerType::class.java)

        SplashModel.splashScreenCompleteTutorial(profiles)
      }

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
    val accountToolBar: ViewGroup =
      this.root.findViewById(R.id.accountRegistryToolbar)
    val accountRegistryList: RecyclerView =
      this.root.findViewById(R.id.accountRegistryList)
    val accountRegistryProgress: ContentLoadingProgressBar =
      this.root.findViewById(R.id.accountRegistryProgress)
    val searchIcon: ImageView =
      this.root.findViewById(R.id.accountRegistryToolbarSearchIcon)
    val searchTouch: ViewGroup =
      this.root.findViewById(R.id.accountRegistryToolbarSearchIconTouch)
    val searchText: EditText =
      this.root.findViewById(R.id.accountRegistryToolbarSearchText)
    val title: TextView =
      this.root.findViewById(R.id.accountRegistryToolbarTitle)
    val backButton: View =
      this.root.findViewById(R.id.accountRegistryToolbarBackIconTouch)

    var onSearchChanged: (String) -> Unit = {}

    init {
      this.backButton.visibility = View.GONE

      this.searchTouch.setOnClickListener {
        if (this.searchText.isVisible) {
          this.searchBoxClose()
          this.searchText.setText("")
        } else {
          this.searchBoxOpen()
        }
      }

      this.searchText.addTextChangedListener {
        this.onSearchChanged.invoke(this.searchText.text.trim().toString())
      }

      this.searchText.setOnEditorActionListener { v, actionId, event ->
        return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_DONE) {
          this.keyboardHide()
          this.onSearchChanged.invoke(this.searchText.text.trim().toString())
          true
        } else {
          false
        }
      }
    }

    private fun searchBoxOpen() {
      this.searchIcon.setImageResource(R.drawable.xmark)
      this.searchText.visibility = View.VISIBLE
      this.title.visibility = View.INVISIBLE

      this.searchText.postDelayed({ this.searchText.requestFocus() }, 100)
      this.searchText.postDelayed({ this.keyboardShow() }, 100)
    }

    private fun searchBoxClose() {
      this.searchIcon.setImageResource(R.drawable.magnifying_glass)
      this.searchText.visibility = View.INVISIBLE
      this.title.visibility = View.VISIBLE

      this.searchText.postDelayed({ this.keyboardHide() }, 100)
    }

    private fun keyboardHide() {
      try {
        WindowInsetsControllerCompat(this.activity.window, this.searchText)
          .hide(WindowInsetsCompat.Type.ime())
      } catch (e: Throwable) {
        // No sensible response.
      }
    }

    private fun keyboardShow() {
      try {
        WindowInsetsControllerCompat(this.activity.window, this.searchText)
          .show(WindowInsetsCompat.Type.ime())
      } catch (e: Throwable) {
        // No sensible response.
      }
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

  private data class TutorialViews(
    private val root: ViewGroup,
    private val onTutorialFinished: () -> Unit
  ) {
    val tutorialSkip =
      this.root.findViewById<View>(R.id.tutorialSkipTouch)
    val tutorialNext =
      this.root.findViewById<View>(R.id.tutorialPageNextTouch)
    val tutorialPrevious =
      this.root.findViewById<View>(R.id.tutorialPagePreviousTouch)
    val tutorialStep1Group =
      this.root.findViewById<Group>(R.id.tutorialStep1Group)
    val tutorialStep2Group =
      this.root.findViewById<Group>(R.id.tutorialStep2Group)
    val tutorialStep3Group =
      this.root.findViewById<Group>(R.id.tutorialStep3Group)
    val tutorialPhoneImage =
      this.root.findViewById<ImageView>(R.id.tutorialPhoneBackgroundView)
    val tutorialStep2Text =
      this.root.findViewById<TextView>(R.id.tutorialStep2Text1)

    init {
      this.tutorialStep1Group.visibility = View.VISIBLE
      this.tutorialStep2Group.visibility = View.INVISIBLE
      this.tutorialStep3Group.visibility = View.INVISIBLE

      val step2Text = StringBuilder()
      step2Text.append("<br/>")
      step2Text.append(root.context.getString(R.string.splashStep2TextSegment1))
      step2Text.append("<br/>")
      step2Text.append(root.context.getString(R.string.splashStep2TextSegment2))
      step2Text.append("<br/>")
      step2Text.append("<font color=\"#2126ad\">")
      step2Text.append(root.context.getString(R.string.splashStep2TextSegment3))
      step2Text.append("</font>")
      step2Text.append("<br/>")
      step2Text.append(root.context.getString(R.string.splashStep2TextSegment4))

      this.tutorialStep2Text.text =
        Html.fromHtml(step2Text.toString(), Html.FROM_HTML_MODE_LEGACY)

      /*
       * Set a custom image scaling matrix that will scale the image from the top-left
       * corner. This ensures the width of the image always matches the image view, and
       * the image expands off the page downwards.
       */

      this.tutorialPhoneImage.doOnLayout {
        val drawable = this.tutorialPhoneImage.drawable
        val matrix = Matrix()
        val scale = this.tutorialPhoneImage.width.toFloat() / drawable.intrinsicWidth
        matrix.setScale(scale, scale)
        this.tutorialPhoneImage.imageMatrix = matrix
      }

      this.tutorialSkip.setOnClickListener {
        this.onTutorialFinished.invoke()
      }
      this.tutorialNext.setOnClickListener {
        val nextIndex = SplashTutorialModel.pageNext()
        if (nextIndex == null) {
          this.onTutorialFinished.invoke()
          return@setOnClickListener
        }
        this.showViewsForIndex(nextIndex)
      }
      this.tutorialPrevious.setOnClickListener {
        this.showViewsForIndex(SplashTutorialModel.pagePrevious())
      }

      this.showViewsForIndex(0)
    }

    private fun showViewsForIndex(
      index: Int
    ) {
      when (index) {
        0 -> {
          this.tutorialPrevious.visibility = View.INVISIBLE
          this.tutorialNext.visibility = View.VISIBLE
          this.tutorialStep1Group.visibility = View.VISIBLE
          this.tutorialStep2Group.visibility = View.INVISIBLE
          this.tutorialStep3Group.visibility = View.INVISIBLE
        }

        1 -> {
          this.tutorialPrevious.visibility = View.VISIBLE
          this.tutorialNext.visibility = View.VISIBLE
          this.tutorialStep1Group.visibility = View.INVISIBLE
          this.tutorialStep2Group.visibility = View.VISIBLE
          this.tutorialStep3Group.visibility = View.INVISIBLE
        }

        2 -> {
          this.tutorialPrevious.visibility = View.VISIBLE
          this.tutorialNext.visibility = View.VISIBLE
          this.tutorialStep1Group.visibility = View.INVISIBLE
          this.tutorialStep2Group.visibility = View.INVISIBLE
          this.tutorialStep3Group.visibility = View.VISIBLE
        }
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

    SplashTutorialModel.pageReset()

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

  private fun onSplashScreenStatusChanged(
    status: SplashModel.SplashScreenStatus
  ) {
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

      SPLASH_SCREEN_TUTORIAL -> {
        val services =
          Services.serviceDirectory()
        val profiles =
          services.requireService(ProfilesControllerType::class.java)

        if (SplashModel.userHasCompletedTutorial(profiles)) {
          SplashModel.splashScreenCompleteTutorial(profiles)
        } else {
          this.openTutorial()
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

  private fun openTutorial() {
    this.splashHolder.removeAllViews()
    this.splashHolder.addView(this.tutorialViewRoot)
  }

  private fun onBootEvent(
    event: BootEvent
  ) {
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

    val adapter = FilterableAccountListAdapter(
      imageLoader = services.requireService(ImageLoaderType::class.java),
      onItemClicked = { description ->
        this.onUserSelectedLibraryForAddition(description)
      }
    )

    /*
     * Subscribe to the account provider list and registry in order to receive lists of
     * providers.
     */

    val registry =
      services.requireService(AccountProviderRegistryType::class.java)
    val buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)

    this.subscriptions.add(
      SplashModel.accountProviders.subscribe { _, providers ->
        adapter.submitList(providers)
      }
    )

    /*
     * Create a status attribute that guarantees registry status updates are observed on the
     * UI thread. Eventually, the library registry will be retrofitted with attributes that
     * will make this unnecessary.
     */

    val status: AttributeType<AccountProviderRegistryStatus> =
      MainAttributes.attributes.withValue(Idle)

    this.subscriptions.add(MainAttributes.wrapAttribute(registry.statusAttribute, status))
    this.subscriptions.add(status.subscribe { _, statusNow ->
      when (statusNow) {
        Idle -> {
          this.selectionListViews.accountRegistryProgress.hide()
        }

        Refreshing -> {
          this.selectionListViews.accountRegistryProgress.show()
        }
      }
    }
    )
    this.subscriptions.add(
      SplashModel.accountProvidersLoad(
        buildConfig = buildConfig,
        registry = registry
      )
    )

    this.selectionListViews.onSearchChanged = { text ->
      adapter.filterList { account ->
        if (account == null) {
          return@filterList true
        }
        if (text.isBlank()) {
          return@filterList true
        }
        if (account.title.contains(text, ignoreCase = true)) {
          return@filterList true
        }
        return@filterList false
      }
    }

    this.selectionListViews.accountRegistryList.adapter = adapter
    this.selectionListViews.accountRegistryList.setHasFixedSize(true)
    this.selectionListViews.accountRegistryList.layoutManager =
      LinearLayoutManager(this.context)
    this.selectionListViews.accountRegistryList.addItemDecoration(
      SpaceItemDecoration(RecyclerView.VERTICAL, this.requireContext())
    )
  }

  private fun onUserSelectedLibraryForAddition(
    description: AccountProviderDescription
  ) {
    this.selectionListViews.accountRegistryList.visibility = View.INVISIBLE
    this.selectionListViews.accountRegistryProgress.show()

    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    // XXX: Should we wait to determine if the account was actually created?
    profiles.profileAccountCreate(description.id)

    SplashModel.splashScreenCompleteLibrarySelection(profiles)
  }

  private fun onBootInProgress(
    event: BootEvent.BootInProgress
  ) {
    this.splashViews.splashText.text = event.message
  }

  private fun onBootFailed(
    event: BootEvent.BootFailed
  ) {
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

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result {
    return BACK_BUTTON_NOT_CONSUMED
  }
}
