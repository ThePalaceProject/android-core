package org.thepalaceproject.ui.main

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator.TabConfigurationStrategy
import org.thepalaceproject.ui.R
import org.thepalaceproject.ui.books.UIBooksFragment
import org.thepalaceproject.ui.catalog.UICatalogFragment
import org.thepalaceproject.ui.reservations.UIReservationsFragment
import org.thepalaceproject.ui.settings.UISettingsFragment

class UIMainTabsAdapter(fragment: UIMainTabsFragment) : FragmentStateAdapter(fragment), TabConfigurationStrategy {

  override fun getItemCount(): Int {
    return 4
  }

  override fun createFragment(position: Int): Fragment {
    return when (position) {
      0 -> UICatalogFragment()
      1 -> UIBooksFragment()
      2 -> UIReservationsFragment()
      3 -> UISettingsFragment()
      else -> throw IllegalStateException("Unrecognized tab position: $position")
    }
  }

  override fun onConfigureTab(tab: TabLayout.Tab, position: Int) {
    when (position) {
      0 -> {
        tab.setIcon(R.drawable.tab_catalog)
        tab.setText(R.string.uiTabCatalog)
      }
      1 -> {
        tab.setIcon(R.drawable.tab_books)
        tab.setText(R.string.uiTabMyBooks)
      }
      2 -> {
        tab.setIcon(R.drawable.tab_holds)
        tab.setText(R.string.uiTabReservations)
      }
      3 -> {
        tab.setIcon(R.drawable.tab_settings)
        tab.setText(R.string.uiTabSettings)
      }
    }
  }
}
