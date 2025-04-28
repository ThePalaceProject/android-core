package org.nypl.simplified.ui.screens

import androidx.fragment.app.Fragment

/**
 * A screen definition represents the combination of a stateless fragment and some mandatory
 * parameters used to display the fragment.
 */

interface ScreenDefinitionType<P, F : Fragment> {

  /**
   * Set up any underlying models that the fragment requires for display.
   */

  fun setup()

  /**
   * @return The parameters for this screen
   */

  fun parameters(): P

  /**
   * @return A stateless fragment that can be used to display this screen
   */

  fun fragment(): F
}
