package org.nypl.simplified.ui.splash

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import org.librarysimplified.ui.R
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.slf4j.LoggerFactory

class SplashFragment : Fragment(R.layout.splash_fragment), FragmentResultListener {

  private val logger = LoggerFactory.getLogger(SplashFragment::class.java)
  private val listener: FragmentListenerType<SplashEvent> by this.fragmentListeners()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    this.childFragmentManager.setFragmentResultListener(
      "",
      this,
      this::onFragmentResult
    )
  }

  override fun onFragmentResult(requestKey: String, result: Bundle) {
    when (this.childFragmentManager.fragments.last()) {
      is BootFragment -> this.onBootCompleted()
    }
  }

  private fun onBootCompleted() {
    this.listener.post(SplashEvent.SplashCompleted)
  }
}
