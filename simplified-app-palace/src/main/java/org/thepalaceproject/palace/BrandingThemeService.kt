package org.thepalaceproject.palace

import org.nypl.simplified.ui.branding.BrandingThemeOverrideServiceType
import org.nypl.simplified.ui.theme.ThemeValue

class BrandingThemeService : BrandingThemeOverrideServiceType {
  override fun overrideTheme(): ThemeValue {
    return ThemeValue(
      name = "palace",
      color = R.color.colorPrimary,
      colorLight = R.color.colorPrimaryLight,
      colorDark = R.color.colorPrimaryDark,
      themeWithActionBar = R.style.PalaceTheme_ActionBar,
      themeWithNoActionBar = R.style.PalaceTheme_NoActionBar
    )
  }
}
