package org.nypl.simplified.ui.errorpage

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import org.librarysimplified.ui.errorpage.R
import org.nypl.simplified.ui.screen.ScreenEdgeToEdgeFix

/**
 * A convenient base activity used to show error pages.
 */

class ErrorPageActivity : AppCompatActivity(R.layout.error_host) {

  private lateinit var root: View
  private lateinit var errorFragment: ErrorPageFragment

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    this.errorFragment = ErrorPageFragment()
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.errorHolder, this.errorFragment, "ERROR_MAIN")
      .commit()

    this.root =
      this.findViewById(R.id.errorHolderRoot)

    ScreenEdgeToEdgeFix.edgeToEdge(this.root)
  }
}
