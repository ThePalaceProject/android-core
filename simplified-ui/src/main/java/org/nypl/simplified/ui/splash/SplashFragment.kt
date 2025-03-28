package org.nypl.simplified.ui.splash

import android.app.Activity
import android.os.Bundle
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
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
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.accounts.FilterableAccountListAdapter
import org.nypl.simplified.ui.accounts.SpaceItemDecoration
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.main.MainApplication
import org.nypl.simplified.ui.main.MainAttributes
import java.util.concurrent.Executors
import kotlin.math.max

class SplashFragment : Fragment() {

  private lateinit var selectionListViewRoot: ViewGroup
  private lateinit var selectionListViews: LibrarySelectionViews
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
      TutorialViews(this.tutorialViewRoot)

    this.selectionListViewRoot =
      inflater.inflate(R.layout.account_list_registry, container, false) as ViewGroup
    this.selectionListViews =
      LibrarySelectionViews(
        this.requireActivity(),
        this.selectionListViewRoot
      )

    this.splashHolder.removeAllViews()
    this.splashHolder.addView(this.splashViewRoot)
    return this.splashHolder
  }

  private data class LibrarySelectionViews(
    private val activity: Activity,
    private val root: ViewGroup
  ) {
    val accountToolBar: ViewGroup =
      this.root.findViewById(R.id.accountListToolbar)
    val accountRegistryList: RecyclerView =
      this.root.findViewById(R.id.accountRegistryList)
    val accountRegistryProgress: ContentLoadingProgressBar =
      this.root.findViewById(R.id.accountRegistryProgress)
    val searchIcon: ImageView =
      this.root.findViewById(R.id.accountListToolbarSearchIcon)
    val searchTouch: ViewGroup =
      this.root.findViewById(R.id.accountListToolbarSearchIconTouch)
    val searchText: EditText =
      this.root.findViewById(R.id.accountListToolbarSearchText)

    var onSearchChanged: (String) -> Unit = {}

    init {
      this.searchTouch.setOnClickListener {
        if (this.searchText.isVisible) {
          this.searchBoxClose()
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

      this.searchText.postDelayed({ this.searchText.requestFocus() }, 100)
      this.searchText.postDelayed({ this.keyboardShow() }, 100)
    }

    private fun searchBoxClose() {
      this.searchIcon.setImageResource(R.drawable.magnifying_glass)
      this.searchText.visibility = View.INVISIBLE

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

  private data class TutorialViews(
    private val root: ViewGroup
  ) {
    val tutorialPager =
      this.root.findViewById<ViewPager2>(R.id.tutorialViewPager)
    val tutorialSkip =
      this.root.findViewById<ImageView>(R.id.tutorialSkip)
    val tutorialTabs =
      this.root.findViewById<TabLayout>(R.id.tutorialTabLayout)
    val tutorialAdapter =
      SplashTutorialPageAdapter()

    init {
      this.tutorialPager.adapter = this.tutorialAdapter

      TabLayoutMediator(this.tutorialTabs, this.tutorialPager) { tab, position ->
        tab.contentDescription = this.root.resources.getString(
          when (position) {
            0 -> {
              R.string.contentDescriptionStep1
            }

            1 -> {
              R.string.contentDescriptionStep2
            }

            else -> {
              R.string.contentDescriptionStep3
            }
          }
        )
      }.attach()
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
  }

  private fun onBootEvent(
    event: BootEvent
  ) {
    UIThread.checkIsUIThread()

    return when (event) {
      is BootEvent.BootCompleted -> {
        this.onBootCompleted()
      }

      is BootEvent.BootFailed -> {
        this.onBootFailed(event)
      }

      is BootEvent.BootInProgress -> {
        this.onBootInProgress(event)
      }
    }
  }

  private fun onBootCompleted() {
    /*
     * If the user has already done the tutorial and library selection at some point in the past,
     * skip everything and request that the splash screen be closed.
     */

    if (this.userHasCompletedOnboarding()) {
      this.splashScreenFinishNow()
      return
    }

    /*
     * Otherwise, the next step is to show the tutorial.
     */

    this.splashHolder.removeAllViews()
    this.splashHolder.addView(this.tutorialViewRoot)

    this.tutorialViews.tutorialSkip.setOnClickListener {
      this.splashScreenOpenLibrarySelection()
    }

    this.tutorialViews.tutorialPager.registerOnPageChangeCallback(
      object : ViewPager2.OnPageChangeCallback() {
        private var isSwipingOnLastPage = false

        override fun onPageScrolled(
          position: Int,
          positionOffset: Float,
          positionOffsetPixels: Int
        ) {
          if (this.isSwipingOnLastPage) {
            this@SplashFragment.splashScreenOpenLibrarySelection()
            return
          }

          val endPageIndex =
            max(0, this@SplashFragment.tutorialViews.tutorialAdapter.itemCount - 1)
          this.isSwipingOnLastPage =
            position == endPageIndex
        }
      }
    )
  }

  private fun splashScreenOpenLibrarySelection() {
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
     * providers. Refreshing the registry is a synchronous call, so we (unfortunately) need
     * to create a temporary background executor on which to execute the refresh. We ensure
     * that the executor is closed whenever the fragment is.
     */

    val registry =
      services.requireService(AccountProviderRegistryType::class.java)
    val registryBackground =
      Executors.newFixedThreadPool(1)

    this.subscriptions.add(AutoCloseable { registryBackground.shutdown() })
    this.subscriptions.add(
      SplashModel.accountProviders.subscribe { _, providers -> adapter.submitList(providers) }
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
        executor = registryBackground,
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
      SpaceItemDecoration(RecyclerView.VERTICAL, requireContext())
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

    this.splashScreenFinishNow()
  }

  private fun splashScreenFinishNow() {
    this.splashScreenRecordCompletion()
    SplashModel.splashScreenCompleted()
  }

  private fun splashScreenRecordCompletion() {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    profiles.profileUpdate { p ->
      p.copy(preferences = p.preferences.copy(hasSeenLibrarySelectionScreen = true))
    }
  }

  private fun userHasCompletedOnboarding(): Boolean {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    val profile =
      profiles.profileCurrent()
    val preferences =
      profile.preferences()

    return preferences.hasSeenLibrarySelectionScreen
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
}
