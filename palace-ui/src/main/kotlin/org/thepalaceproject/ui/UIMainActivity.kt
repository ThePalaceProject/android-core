package org.thepalaceproject.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.thepalaceproject.ui.main.UIMainTabsFragment

class UIMainActivity : AppCompatActivity(R.layout.ui_main_host) {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
  }

  override fun onStart() {
    super.onStart()

    val tabsFragment = UIMainTabsFragment()
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.uiMainFragmentHolder, tabsFragment, "MAIN")
      .commit()
  }
}
