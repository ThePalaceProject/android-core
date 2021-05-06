package org.lyrasis.raybooks

import org.nypl.simplified.buildconfig.api.BuildConfigOAuthScheme
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.main.BuildConfig

class RayBooksBuildConfigurationService : BuildConfigurationServiceType {
  override val allowAccountsAccess: Boolean
    get() = true
  override val allowAccountsRegistryAccess: Boolean
    get() = true
  override val showDebugBookDetailStatus: Boolean
    get() = false
  override val showSettingsTab: Boolean
    get() = true
  override val showHoldsTab: Boolean
    get() = true
  override val showBooksFromAllAccounts: Boolean
    get() = false
  override val vcsCommit: String
    get() = BuildConfig.GIT_COMMIT
  override val simplifiedVersion: String
    get() = BuildConfig.SIMPLIFIED_VERSION
  override val supportErrorReportEmailAddress: String
    get() = "ray.lee@lyrasis.org"
  override val supportErrorReportSubject: String
    get() = "[raybooks-error]"
  override val oauthCallbackScheme: BuildConfigOAuthScheme
    get() = BuildConfigOAuthScheme("raybooks-oauth")
  override val allowExternalReaderLinks: Boolean
    get() = false
  override val showChangeAccountsUi: Boolean
    get() = true
  override val showAgeGateUi: Boolean
    get() = true
}
