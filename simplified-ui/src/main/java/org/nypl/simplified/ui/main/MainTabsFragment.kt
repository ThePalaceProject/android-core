package org.nypl.simplified.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.catalog.CatalogFragmentHolds
import org.nypl.simplified.ui.catalog.CatalogFragmentMain
import org.nypl.simplified.ui.catalog.CatalogFragmentMyBooks
import org.nypl.simplified.ui.catalog.CatalogOPDSClients
import org.nypl.simplified.ui.catalog.CatalogPart
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_NOT_CONSUMED
import org.nypl.simplified.ui.main.MainTabCategory.TAB_BOOKS
import org.nypl.simplified.ui.main.MainTabCategory.TAB_CATALOG
import org.nypl.simplified.ui.main.MainTabCategory.TAB_RESERVATIONS
import org.nypl.simplified.ui.main.MainTabCategory.TAB_SETTINGS

/**
 * The main fragment that handles the tabbed view. The fragment is responsible for attaching
 * and detaching fragments from the tabbed view, and for routing back button presses to the
 * correct places.
 */

class MainTabsFragment : Fragment(), MainBackButtonConsumerType {

  private lateinit var root: ViewGroup
  private lateinit var tabBooks: TabLayout.Tab
  private lateinit var tabCatalog: TabLayout.Tab
  private lateinit var tabContent: FrameLayout
  private lateinit var tabLayout: TabLayout
  private lateinit var tabReservations: TabLayout.Tab
  private lateinit var tabSettings: TabLayout.Tab

  private var fragmentNow: Fragment? = null

  private var subscriptions: CloseableCollectionType<ClosingResourceFailedException> =
    CloseableCollection.create()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    this.root =
      inflater.inflate(R.layout.main_tabs, container, false) as ViewGroup
    this.tabLayout =
      this.root.findViewById(R.id.mainTabs)
    this.tabContent =
      this.root.findViewById(R.id.mainTabsContent)

    this.tabCatalog =
      this.tabLayout.getTabAt(0)!!
    this.tabBooks =
      this.tabLayout.getTabAt(1)!!
    this.tabReservations =
      this.tabLayout.getTabAt(2)!!
    this.tabSettings =
      this.tabLayout.getTabAt(3)!!

    /*
     * Naturally, because even the simplest things on Android are completely broken, it's
     * not enough to just track the index of the selected tab: Android will screw up that
     * approach by always calling `onTabSelected` with `0` whenever the view is created. Instead,
     * what you have to do is track whether a tab has been _unselected_ and then, if and only
     * if one has, track the selected tab. Otherwise, you'll always set the tab to `0` whenever
     * the view is recreated.
     */

    this.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
      override fun onTabSelected(tab: TabLayout.Tab?) {
        if (tab != null && MainTabModel.tabUnselected != null) {
          MainTabModel.tabSelected = tab.position
          MainTabModel.tabUnselected = null
          this@MainTabsFragment.switchToTab(tab.position)
        }
      }

      override fun onTabUnselected(tab: TabLayout.Tab?) {
        if (tab != null) {
          MainTabModel.tabUnselected = tab.position
        }
      }

      override fun onTabReselected(tab: TabLayout.Tab?) {
        if (tab != null) {
          this@MainTabsFragment.reselectForTab(tab.position)
        }
      }
    })
    return this.root
  }

  override fun onStart() {
    super.onStart()

    /*
     * Restore the selected tab.
     */

    this.switchToTab(MainTabModel.tabSelected ?: 0)

    this.subscriptions = CloseableCollection.create()
    this.subscriptions.add(
      MainNavigation.Settings.navigationStack.subscribe { _, newValue ->
        this.onSettingsTabChanged()
      }
    )
    this.subscriptions.add(
      MainNavigation.tab.subscribe { _, newValue ->
        this.onTabRequested(newValue)
      }
    )
  }

  /**
   * A tab request arrived from the navigation system.
   */

  private fun onTabRequested(
    newValue: MainTabRequest
  ) {
    when (newValue) {
      MainTabRequest.TabAny -> {
        // Do nothing.
      }

      is MainTabRequest.TabForCategory -> {
        this.tabLayout.selectTab(
          when (newValue.category) {
            TAB_CATALOG -> this.tabCatalog
            TAB_BOOKS -> this.tabBooks
            TAB_RESERVATIONS -> this.tabReservations
            TAB_SETTINGS -> this.tabSettings
          }
        )
      }
    }
  }

  private fun reselectForTab(
    tabIndex: Int
  ) {
    val services =
      Services.serviceDirectory()
    val opdsClients =
      services.requireService(CatalogOPDSClients::class.java)
    val profiles =
      services.requireService(ProfilesControllerType::class.java)
    val account =
      profiles.profileCurrent()
        .account(
          profiles.profileCurrent()
            .preferences()
            .mostRecentAccount
        )

    when (tabIndex) {
      0 -> opdsClients.goToRootFeedFor(CatalogPart.CATALOG, account)
      1 -> opdsClients.goToRootFeedFor(CatalogPart.BOOKS, account)
      2 -> opdsClients.goToRootFeedFor(CatalogPart.HOLDS, account)
      3 -> MainNavigation.Settings.goToRoot()
      else -> throw IllegalStateException("Unexpected tab index: $tabIndex")
    }
  }

  private fun onSettingsTabChanged() {
    val index = MainTabModel.tabSelected
    if (index != null) {
      this.setTabFragment(index)
    }
  }

  private fun switchToTab(
    tabIndex: Int
  ) {
    this.tabLayout.selectTab(this.tabLayout.getTabAt(tabIndex))
    this.setTabFragment(tabIndex)
  }

  private fun setTabFragment(
    tabIndex: Int
  ) {
    when (tabIndex) {
      0 -> this.switchFragment(CatalogFragmentMain())
      1 -> this.switchFragment(CatalogFragmentMyBooks())
      2 -> this.switchFragment(CatalogFragmentHolds())
      3 -> this.switchFragment(MainNavigation.Settings.currentScreen().fragment())
      else -> throw IllegalStateException("Unexpected tab index: $tabIndex")
    }
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.close()
  }

  private fun switchFragment(
    fragment: Fragment
  ) {
    this.fragmentNow = fragment
    this.childFragmentManager.beginTransaction()
      .replace(R.id.mainTabsContent, fragment)
      .commit()
  }

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result {
    val current = this.fragmentNow
    if (current is MainBackButtonConsumerType) {
      return current.onBackButtonPressed()
    }
    return BACK_BUTTON_NOT_CONSUMED
  }
}
