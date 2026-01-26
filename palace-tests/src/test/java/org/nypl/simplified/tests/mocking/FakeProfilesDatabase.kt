package org.nypl.simplified.tests.mocking

import com.io7m.jfunctional.OptionType
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfileType
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import java.io.File
import java.util.SortedMap
import java.util.TreeMap

class FakeProfilesDatabase : ProfilesDatabaseType {

  val profileMap =
    TreeMap<ProfileID, ProfileType>()

  override fun anonymousProfileEnabled(): ProfilesDatabaseType.AnonymousProfileEnabled {
    TODO("Not yet implemented")
  }

  override fun anonymousProfile(): ProfileType {
    TODO("Not yet implemented")
  }

  override fun directory(): File {
    TODO("Not yet implemented")
  }

  override fun profiles(): SortedMap<ProfileID, ProfileType> {
    return this.profileMap
  }

  override fun createProfile(
    accountProvider: AccountProviderType,
    displayName: String
  ): ProfileType {
    TODO("Not yet implemented")
  }

  override fun findProfileWithDisplayName(displayName: String): OptionType<ProfileType> {
    TODO("Not yet implemented")
  }

  override fun setProfileCurrent(profile: ProfileID) {
    TODO("Not yet implemented")
  }

  override fun currentProfile(): OptionType<ProfileType> {
    TODO("Not yet implemented")
  }

  override fun currentProfileUnsafe(): ProfileType {
    TODO("Not yet implemented")
  }
}
