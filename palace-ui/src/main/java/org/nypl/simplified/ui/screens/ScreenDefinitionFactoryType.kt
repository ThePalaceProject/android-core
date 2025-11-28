package org.nypl.simplified.ui.screens

import androidx.fragment.app.Fragment

/**
 * A factory of screen definitions.
 */

interface ScreenDefinitionFactoryType<P, F : Fragment> {

  /**
   * Create a screen definition.
   */

  fun createScreenDefinition(p: P): ScreenDefinitionType<P, F>
}
