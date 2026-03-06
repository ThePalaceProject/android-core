package org.nypl.simplified.profiles

import net.jcip.annotations.GuardedBy
import org.joda.time.LocalDateTime
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.accounts.database.api.AccountsDatabaseException
import org.nypl.simplified.accounts.database.api.AccountsDatabaseNonexistentException
import org.nypl.simplified.accounts.database.api.AccountsDatabaseType
import org.nypl.simplified.analytics.api.AnalyticsEvent
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.profiles.api.ProfileDescription
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.profiles.api.ProfileType
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.SortedMap

/**
 * A single entry in the profiles database.
 */

internal class Profile internal constructor(
  private var owner: ProfilesDatabase?,
  override val id: ProfileID,
  override val directory: File,
  private val analytics: AnalyticsType,
  private val accounts: AccountsDatabaseType,
  private var initialDescription: ProfileDescription
) : ProfileType {

  private val logger =
    LoggerFactory.getLogger(Profile::class.java)

  init {

    /*
     * If an account is deleted for any reason, and the profile preferences are not updated,
     * then it's possible for the preferences to refer to the destroyed account. This checks
     * for that unlikely occurrence and fixes the preferences.
     */

    val preferences = this.initialDescription.preferences
    val accountMap = this.accounts.accounts()
    if (!accountMap.containsKey(preferences.mostRecentAccount)) {
      val newAccountId = accountMap.firstKey()
      this.logger.debug(
        "fixing dangling account ID: {} -> {}",
        preferences.mostRecentAccount.uuid,
        newAccountId.uuid
      )
      this.initialDescription = this.initialDescription.copy(
        preferences = preferences.copy(mostRecentAccount = newAccountId)
      )
    }
  }

  private val descriptionLock: Any = Any()

  @GuardedBy("descriptionLock")
  private var descriptionCurrent: ProfileDescription = this.initialDescription

  override fun accounts(): SortedMap<AccountID, AccountType> {
    return this.accounts.accounts()
  }

  override fun accountsByProvider(): SortedMap<URI, AccountType> {
    return this.accounts.accountsByProvider()
  }

  @Throws(AccountsDatabaseNonexistentException::class)
  override fun account(accountId: AccountID): AccountType {
    return this.accounts()[accountId]
      ?: throw AccountsDatabaseNonexistentException("Nonexistent account: $accountId")
  }

  override fun accountsDatabase(): AccountsDatabaseType {
    return this.accounts
  }

  override fun setDescription(newDescription: ProfileDescription) {
    synchronized(this.descriptionLock) {
      ProfilesDatabases.writeDescription(this.directory, newDescription)
      this.descriptionCurrent = newDescription
    }

    this.logProfileModified()
  }

  @Throws(AccountsDatabaseException::class)
  override fun createAccount(accountProvider: AccountProviderType): AccountType {
    val account = this.accounts.createAccount(accountProvider)
    this.setDescription(
      this.descriptionCurrent.copy(
        preferences = this.descriptionCurrent.preferences.copy(mostRecentAccount = account.id)
      )
    )
    return account
  }

  @Throws(AccountsDatabaseException::class)
  override fun deleteAccountByProvider(accountProvider: URI): AccountID {
    val deleted = this.accounts.deleteAccountByProvider(accountProvider)
    val mostRecent = this.descriptionCurrent.preferences.mostRecentAccount
    if (mostRecent == deleted) {
      this.updateMostRecentAccount()
    }
    return deleted
  }

  private fun updateMostRecentAccount() {
    val accounts =
      this.accounts.accounts().values
    val mostRecent =
      if (accounts.size > 1) {
        // Return the first account created from a non-default provider
        accounts.first { it.provider.id != this.owner!!.defaultAccountProvider.id }
      } else {
        // Return the first account
        accounts.first()
      }
    this.setDescription(
      this.descriptionCurrent.copy(
        preferences = this.descriptionCurrent.preferences.copy(
          mostRecentAccount = mostRecent.id
        )
      )
    )
  }

  override fun compareTo(other: ProfileReadableType): Int {
    return 0
  }

  override fun description(): ProfileDescription {
    return synchronized(this.descriptionLock) {
      this.descriptionCurrent
    }
  }

  private fun logProfileModified() {
    this.analytics.publishEvent(
      AnalyticsEvent.ProfileUpdated(
        timestamp = LocalDateTime.now(),
        credentials = null,
        profileUUID = this.id.uuid,
        displayName = "Anonymous",
        birthDate = this.preferences().dateOfBirth?.date?.toString(),
        attributes = this.description().attributes.attributes
      )
    )
  }
}
