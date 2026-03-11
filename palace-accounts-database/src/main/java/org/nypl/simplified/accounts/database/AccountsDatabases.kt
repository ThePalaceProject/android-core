package org.nypl.simplified.accounts.database

import android.app.Application
import io.reactivex.subjects.Subject
import org.librarysimplified.http.api.LSHTTPClientType
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentialsStoreType
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.database.api.AccountsDatabaseException
import org.nypl.simplified.accounts.database.api.AccountsDatabaseFactoryType
import org.nypl.simplified.accounts.database.api.AccountsDatabaseType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.books.book_database.BookDatabases
import org.nypl.simplified.books.book_database.api.BookDatabaseFactoryType
import org.nypl.simplified.books.formats.BookFormatSupportType
import java.io.File

/**
 * The default implementation of the {@link AccountsDatabaseFactoryType} interface.
 */

object AccountsDatabases : AccountsDatabaseFactoryType {

  @Throws(AccountsDatabaseException::class)
  override fun openDatabase(
    accountAuthenticationCredentialsStore: AccountAuthenticationCredentialsStoreType,
    accountEvents: Subject<AccountEvent>,
    accountProviders: AccountProviderRegistryType,
    bookDatabases: BookDatabaseFactoryType,
    bookFormatSupport: BookFormatSupportType,
    context: Application,
    directory: File,
    directoryGraveyard: File,
    httpClient: LSHTTPClientType,
  ): AccountsDatabaseType {
    return AccountsDatabase.open(
      accountCredentials = accountAuthenticationCredentialsStore,
      accountEvents = accountEvents,
      accountProviders = accountProviders,
      bookDatabases = bookDatabases,
      bookFormatSupport = bookFormatSupport,
      context = context,
      directory = directory,
      directoryGraveyard = directoryGraveyard,
      httpClient = httpClient,
    )
  }

  @Throws(AccountsDatabaseException::class)
  override fun openDatabase(
    accountAuthenticationCredentialsStore: AccountAuthenticationCredentialsStoreType,
    accountEvents: Subject<AccountEvent>,
    accountProviders: AccountProviderRegistryType,
    bookFormatSupport: BookFormatSupportType,
    context: Application,
    directory: File,
    directoryGraveyard: File,
    httpClient: LSHTTPClientType,
  ): AccountsDatabaseType {
    return AccountsDatabase.open(
      accountCredentials = accountAuthenticationCredentialsStore,
      accountEvents = accountEvents,
      accountProviders = accountProviders,
      bookDatabases = BookDatabases,
      bookFormatSupport = bookFormatSupport,
      context = context,
      directory = directory,
      directoryGraveyard = directoryGraveyard,
      httpClient = httpClient,
    )
  }
}
