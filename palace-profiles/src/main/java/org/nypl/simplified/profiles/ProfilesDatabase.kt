package org.nypl.simplified.profiles

import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.profiles.api.ProfileType
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import java.io.File

/**
 * The default implementation of the [ProfilesDatabaseType] interface.
 */

internal class ProfilesDatabase internal constructor(
  private val accountProviders: AccountProviderRegistryType,
  private val directory: File,
  private val profile: Profile,
) : ProfilesDatabaseType {
  internal val defaultAccountProvider: AccountProviderType =
    this.accountProviders.defaultProvider

  override fun anonymousProfile(): ProfileType = this.profile

  override fun directory(): File = this.directory

  override fun currentProfile(): ProfileType = this.profile
}
