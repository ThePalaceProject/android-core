package org.nypl.simplified.notifications

import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.profiles.api.ProfileReadableType

interface NotificationTokenHTTPCallsType {
  fun registerFCMTokenForProfileAccounts(profile: ProfileReadableType)

  fun registerFCMTokenForProfileAccount(account: AccountType)

  fun deleteFCMTokenForProfileAccount(account: AccountType)
}
