package org.thepalaceproject.palace

import org.librarysimplified.ui.BuildConfig
import org.nypl.simplified.buildconfig.api.BuildConfigOAuthScheme
import org.nypl.simplified.buildconfig.api.BuildConfigurationAccountsRegistryURIs
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import java.net.URI

class PalaceBuildConfigurationService : BuildConfigurationServiceType {
  override val libraryRegistry: BuildConfigurationAccountsRegistryURIs
    get() = BuildConfigurationAccountsRegistryURIs(
      registry = URI("https://registry.palaceproject.io/libraries"),
      registryQA = URI("https://registry.palaceproject.io/libraries/qa")
    )
  override val allowAccountsAccess: Boolean
    get() = true
  override val allowAccountsRegistryAccess: Boolean
    get() = true
  override val featuredLibrariesIdsList: List<String>
    get() = listOf(
      "urn:uuid:6b849570-070f-43b4-9dcc-7ebb4bca292e" // Palace Bookshelf
    )
  override val showDebugBookDetailStatus: Boolean
    get() = false
  override val showBooksFromAllAccounts: Boolean
    get() = false
  override val vcsCommit: String
    get() = BuildConfig.SIMPLIFIED_GIT_COMMIT
  override val simplifiedVersion: String
    get() = BuildConfig.SIMPLIFIED_VERSION
  override val supportErrorReportEmailAddress: String
    get() = "logs@thepalaceproject.org"
  override val supportErrorReportSubject: String
    get() = "[palace-error]"
  override val oauthCallbackScheme: BuildConfigOAuthScheme
    get() = BuildConfigOAuthScheme("palace-oauth")
  override val allowExternalReaderLinks: Boolean
    get() = false
  override val showChangeAccountsUi: Boolean
    get() = true
  override val showAgeGateUi: Boolean
    get() = true
  override val brandingAppIcon: Int
    get() = org.librarysimplified.ui.R.drawable.main_icon
}
