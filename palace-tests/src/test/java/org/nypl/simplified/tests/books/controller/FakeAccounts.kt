package org.nypl.simplified.tests.books.controller

import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.api.AccountType
import java.net.URI
import java.util.UUID

object FakeAccounts {

  fun fakeAccountProvider(): AccountProviderType {
    val provider = Mockito.mock(AccountProviderType::class.java)!!

    Mockito.`when`(provider.id)
      .thenReturn(URI.create("urn:fake"))

    return provider
  }

  fun fakeAccount(): AccountType {
    val account = Mockito.mock(AccountType::class.java)!!

    Mockito.`when`(account.id)
      .thenReturn(AccountID(UUID.randomUUID()))

    return account
  }
}
