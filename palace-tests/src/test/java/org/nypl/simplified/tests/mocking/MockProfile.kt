package org.nypl.simplified.tests.mocking

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.accounts.database.api.AccountsDatabaseNonexistentException
import org.nypl.simplified.accounts.database.api.AccountsDatabaseType
import org.nypl.simplified.profiles.api.ProfileAttributes
import org.nypl.simplified.profiles.api.ProfileDescription
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfilePreferences
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.profiles.api.ProfileType
import org.nypl.simplified.reader.api.ReaderPreferences
import org.nypl.simplified.tests.TestDirectories
import java.io.File
import java.net.URI
import java.util.SortedMap
import java.util.UUID

class MockProfile(
  override val id: ProfileID,
  accountCount: Int
) : ProfileType {

  override fun setDescription(newDescription: ProfileDescription) {
    check(!this.deleted) { "Profile must not be deleted" }

    TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
  }

  override fun description(): ProfileDescription {
    check(!this.deleted) { "Profile must not be deleted" }

    return ProfileDescription(
      displayName = "Profile ${id.uuid}",
      preferences = ProfilePreferences(
        dateOfBirth = null,
        showTestingLibraries = false,
        hasSeenLibrarySelectionScreen = false,
        hasSeenNotificationScreen = false,
        readerPreferences = ReaderPreferences.builder().build(),
        mostRecentAccount = this.accounts.firstKey(),
        playbackRates = hashMapOf(),
        isManualLCPPassphraseEnabled = false
      ),
      attributes = ProfileAttributes(sortedMapOf())
    )
  }

  override fun delete() {
    this.deleted = true
  }

  private var deleted: Boolean

  val accountList: MutableList<MockAccount> =
    IntRange(1, accountCount)
      .toList()
      .map { MockAccount(TestDirectories.temporaryDirectory(), AccountID(UUID.randomUUID())) }
      .toMutableList()

  val accounts: SortedMap<AccountID, MockAccount> =
    this.accountList.map { account -> Pair(account.id, account) }
      .toMap()
      .toSortedMap()

  override fun accountsDatabase(): AccountsDatabaseType {
    check(!this.deleted) { "Profile must not be deleted" }
    TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
  }

  override fun createAccount(accountProvider: AccountProviderType): AccountType {
    check(!this.deleted) { "Profile must not be deleted" }
    TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
  }

  override fun deleteAccountByProvider(accountProvider: URI): AccountID {
    check(!this.deleted) { "Profile must not be deleted" }
    TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
  }

  override val isAnonymous: Boolean
    get() = TODO("not implemented") // To change initializer of created properties use File | Settings | File Templates.

  override val isCurrent: Boolean
    get() = TODO("not implemented") // To change initializer of created properties use File | Settings | File Templates.

  override val directory: File
    get() = TODO("not implemented") // To change initializer of created properties use File | Settings | File Templates.

  override val displayName: String
    get() = "Profile ${id.uuid}"

  override fun accounts(): SortedMap<AccountID, AccountType> {
    check(!this.deleted) { "Profile must not be deleted" }
    return this.accounts as SortedMap<AccountID, AccountType>
  }

  override fun accountsByProvider(): SortedMap<URI, AccountType> {
    check(!this.deleted) { "Profile must not be deleted" }
    TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
  }

  override fun account(accountId: AccountID): AccountType {
    check(!this.deleted) { "Profile must not be deleted" }
    return this.accounts[accountId]
      ?: throw AccountsDatabaseNonexistentException("No such account!")
  }

  override fun compareTo(other: ProfileReadableType): Int {
    return this.id.compareTo(other.id)
  }
}
