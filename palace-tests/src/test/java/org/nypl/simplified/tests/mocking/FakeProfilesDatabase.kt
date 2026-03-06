package org.nypl.simplified.tests.mocking

import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfileType
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import java.io.File
import java.util.TreeMap

class FakeProfilesDatabase : ProfilesDatabaseType {

  lateinit var profile: ProfileType

  override fun anonymousProfile(): ProfileType {
    TODO("Not yet implemented")
  }

  override fun directory(): File {
    TODO("Not yet implemented")
  }

  override fun currentProfile(): ProfileType {
    return this.profile
  }
}
