package org.nypl.simplified.ui.main

import androidx.activity.ComponentActivity
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import com.io7m.jmulticlose.core.ClosingResourceFailedException
import org.librarysimplified.ui.R

class MainActivity : ComponentActivity(R.layout.main_host) {

  private var subscriptions: CloseableCollectionType<ClosingResourceFailedException> =
    CloseableCollection.create()

  override fun onStart() {
    super.onStart()

    this.subscriptions = CloseableCollection.create()
  }

  @Deprecated("This method has been deprecated by clueless \"engineers\".")
  override fun onBackPressed() {
    // Do nothing, currently
  }

  override fun onStop() {
    super.onStop()

    this.subscriptions.close()
  }
}
