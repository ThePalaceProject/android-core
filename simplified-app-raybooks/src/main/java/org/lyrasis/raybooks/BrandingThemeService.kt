package org.lyrasis.raybooks

import org.nypl.simplified.ui.branding.BrandingThemeOverrideServiceType
import org.nypl.simplified.ui.theme.ThemeValue

class BrandingThemeService : BrandingThemeOverrideServiceType {
  override fun overrideTheme(): ThemeValue {
    return ThemeValue(
      name = "raybooks",
      color = R.color.colorPrimary,
      colorLight = R.color.colorPrimaryLight,
      colorDark = R.color.colorPrimaryDark,
      themeWithActionBar = R.style.RayBooksTheme_ActionBar,
      themeWithNoActionBar = R.style.RayBooksTheme_NoActionBar
    )
  }
}
