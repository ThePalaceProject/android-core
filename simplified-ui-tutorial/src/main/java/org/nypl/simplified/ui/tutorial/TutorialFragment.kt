package org.nypl.simplified.ui.tutorial

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners

class TutorialFragment : Fragment(R.layout.tutorial_fragment) {

  private val listener: FragmentListenerType<TutorialEvent> by fragmentListeners()

  private lateinit var tutorialViewPager: ViewPager2
  private lateinit var skipButton: ImageView

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.tutorialViewPager =
      view.findViewById(R.id.tutorial_view_pager)
    this.skipButton =
      view.findViewById(R.id.skip_button)

    setupUI()
  }

  private fun setupUI() {

    tutorialViewPager.adapter = TutorialPageAdapter()

    this.skipButton.setOnClickListener {
      closeTutorial()
    }
  }

  private fun closeTutorial() {
    this.listener.post(TutorialEvent.TutorialCompleted)
  }

}
