package org.thepalaceproject.ui.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.thepalaceproject.ui.R

class UIMainTabsFragment : Fragment(R.layout.ui_main_tabs) {

  private lateinit var tabLayoutMediator: TabLayoutMediator
  private lateinit var viewPager: ViewPager2
  private lateinit var tabs: TabLayout

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.tabs =
      view.findViewById(R.id.uiTabs)
    this.viewPager =
      view.findViewById(R.id.uiTabsViewPager)

    val tabsAdapter =
      UIMainTabsAdapter(this)

    this.viewPager.adapter =
      tabsAdapter
    this.tabLayoutMediator =
      TabLayoutMediator(this.tabs, this.viewPager, tabsAdapter)

    this.tabLayoutMediator.attach()
  }

  override fun onStart() {
    super.onStart()
  }

  override fun onStop() {
    super.onStop()
  }
}
