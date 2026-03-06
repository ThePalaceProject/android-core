package org.nypl.simplified.tests.books.profiles

import android.app.Application
import android.content.Context
import com.io7m.jfunctional.Option
import io.reactivex.subjects.PublishSubject
import org.joda.time.DateTime
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPNetworkAccess
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.database.AccountBundledCredentialsEmpty
import org.nypl.simplified.accounts.database.api.AccountsDatabaseException
import org.nypl.simplified.accounts.database.api.AccountsDatabaseFactoryType
import org.nypl.simplified.accounts.database.api.AccountsDatabaseNonexistentException
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.files.DirectoryUtilities
import org.nypl.simplified.files.FileUtilities
import org.nypl.simplified.profiles.ProfilesDatabases
import org.nypl.simplified.profiles.api.ProfileAnonymousEnabledException
import org.nypl.simplified.profiles.api.ProfileDatabaseDeleteAnonymousException
import org.nypl.simplified.profiles.api.ProfileDatabaseException
import org.nypl.simplified.profiles.api.ProfileDateOfBirth
import org.nypl.simplified.profiles.api.ProfileDescription
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.tests.books.BookFormatsTesting
import org.nypl.simplified.tests.mocking.FakeAccountCredentialStorage
import org.nypl.simplified.tests.mocking.MockAccountProviderRegistry
import org.nypl.simplified.tests.mocking.MockAccountProviders
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.UUID
import kotlin.jvm.java

abstract class ProfilesDatabaseContract {

  private val logger = LoggerFactory.getLogger(ProfilesDatabaseContract::class.java)

  private lateinit var accountEvents: PublishSubject<AccountEvent>
  private lateinit var analytics: AnalyticsType
  private lateinit var credentialStore: FakeAccountCredentialStorage
  private lateinit var profileEvents: PublishSubject<ProfileEvent>
  private lateinit var httpClient: LSHTTPClientType

  protected abstract fun context(): Application

  @BeforeEach
  open fun setup() {
    this.credentialStore = FakeAccountCredentialStorage()
    this.accountEvents = PublishSubject.create()
    this.profileEvents = PublishSubject.create()
    this.analytics = Mockito.mock(AnalyticsType::class.java)

    this.httpClient =
      LSHTTPClients()
        .create(
          Mockito.mock(Context::class.java),
          LSHTTPClientConfiguration("test", "1.0.0", networkAccess = LSHTTPNetworkAccess)
        )
  }

  private fun onAccountResolution(
    id: URI,
    message: String
  ) {
    this.logger.debug("resolution: {}: {}", id, message)
  }

  /**
   * If the profile directory is not a directory, opening it fails.
   */

  @Test
  @Throws(Exception::class)
  fun testOpenExistingNotDirectory() {
    val fileTemp = DirectoryUtilities.directoryCreateTemporary()
    val fileProfiles = File(fileTemp, "profiles")
    FileUtilities.fileWriteUTF8(fileProfiles, "Hello!")

    val ex = Assertions.assertThrows(
      ProfileDatabaseException::class.java,
      Executable {
        ProfilesDatabases.openWithAnonymousProfileEnabled(
          context = this.context(),
          analytics = this.analytics,
          accountEvents = this.accountEvents,
          accountProviders = MockAccountProviders.fakeAccountProviders(),
          accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
          accountCredentialsStore = this.credentialStore,
          accountsDatabases = this.accountsDatabases(),
          bookFormatSupport = BookFormatsTesting.supportsEverything,
          httpClient = this.httpClient,
          directory = fileProfiles
        )
      }
    )
  }

  /**
   * A subdirectory that can't be parsed as a UUID will be migrated.
   */

  @Test
  @Throws(Exception::class)
  fun testOpenExistingBadSubdirectory() {
    val fileTemp = DirectoryUtilities.directoryCreateTemporary()
    val fileProfiles = File(fileTemp, "profiles")

    fileProfiles.mkdirs()
    val f_bad = File(fileProfiles, "not-a-number")
    f_bad.mkdirs()

    ProfilesDatabases.openWithAnonymousProfileEnabled(
      context = this.context(),
      analytics = this.analytics,
      accountEvents = this.accountEvents,
      accountProviders = MockAccountProviders.fakeAccountProviders(),
      accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
      accountCredentialsStore = this.credentialStore,
      accountsDatabases = this.accountsDatabases(),
      bookFormatSupport = BookFormatsTesting.supportsEverything,
      httpClient = this.httpClient,
      directory = fileProfiles
    )
  }

  /**
   * A subdirectory that isn't a file causes a failure.
   */

  @Test
  @Throws(Exception::class)
  fun testOpenExistingFileSubdirectory() {
    val fileTemp = DirectoryUtilities.directoryCreateTemporary()
    val fileProfiles = File(fileTemp, "profiles")

    fileProfiles.mkdirs()
    val f_bad = File(fileProfiles, UUID.randomUUID().toString())
    f_bad.writeText("Not a profile, clearly.")

    val ex = Assertions.assertThrows(
      ProfileDatabaseException::class.java,
      Executable {
        ProfilesDatabases.openWithAnonymousProfileEnabled(
          context = this.context(),
          analytics = this.analytics,
          accountEvents = this.accountEvents,
          accountProviders = MockAccountProviders.fakeAccountProviders(),
          accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
          accountCredentialsStore = this.credentialStore,
          accountsDatabases = this.accountsDatabases(),
          bookFormatSupport = BookFormatsTesting.supportsEverything,
          httpClient = httpClient, directory = fileProfiles
        )
      }
    )

    Assertions.assertTrue(
      ex.causes()
        .find { e -> e is IOException && e.message!!.contains("Not a directory") } != null
    )
  }

  private fun accountsDatabases(): AccountsDatabaseFactoryType {
    return org.nypl.simplified.accounts.database.AccountsDatabases
  }

  /**
   * A profile with a missing metadata file is ignored.
   */

  @Test
  @Throws(Exception::class)
  fun testOpenExistingJSONMissing() {
    val fileTemp = DirectoryUtilities.directoryCreateTemporary()
    val fileProfiles = File(fileTemp, "profiles")

    fileProfiles.mkdirs()
    val f_0 = File(fileProfiles, "0")
    f_0.mkdirs()

    ProfilesDatabases.openWithAnonymousProfileEnabled(
      context = this.context(),
      analytics = this.analytics,
      accountEvents = this.accountEvents,
      accountProviders = MockAccountProviders.fakeAccountProviders(),
      accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
      accountCredentialsStore = this.credentialStore,
      accountsDatabases = this.accountsDatabases(),
      bookFormatSupport = BookFormatsTesting.supportsEverything,
      httpClient = httpClient, directory = fileProfiles
    )
  }

  /**
   * A profile with a broken metadata file causes an exception.
   */

  @Test
  @Throws(Exception::class)
  fun testOpenExistingJSONUnparseable() {
    val fileTemp = DirectoryUtilities.directoryCreateTemporary()
    val fileProfiles = File(fileTemp, "profiles")

    fileProfiles.mkdirs()
    val f_0 = File(fileProfiles, "0")
    f_0.mkdirs()
    val f_p = File(f_0, "profile.json")
    FileUtilities.fileWriteUTF8(f_p, "} { this is not JSON { } { }")

    val ex = Assertions.assertThrows(
      ProfileDatabaseException::class.java,
      Executable {
        ProfilesDatabases.openWithAnonymousProfileEnabled(
          context = this.context(),
          analytics = this.analytics,
          accountEvents = this.accountEvents,
          accountProviders = MockAccountProviders.fakeAccountProviders(),
          accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
          accountCredentialsStore = this.credentialStore,
          accountsDatabases = this.accountsDatabases(),
          bookFormatSupport = BookFormatsTesting.supportsEverything,
          httpClient = httpClient, directory = fileProfiles
        )
      }
    )

    Assertions.assertTrue(
      ex.causes()
        .find { e -> e is IOException && e.message!!.contains("Could not parse profile: ") } != null
    )
  }

  /**
   * Updating preferences works.
   */

  @Test
  @Throws(Exception::class)
  fun testOpenCreateUpdatePreferences() {
    val fileTemp = DirectoryUtilities.directoryCreateTemporary()
    val fileProfiles = File(fileTemp, "profiles")

    val db = ProfilesDatabases.openWithAnonymousProfileEnabled(
      context = this.context(),
      analytics = this.analytics,
      accountEvents = this.accountEvents,
      accountProviders = MockAccountProviders.fakeAccountProviders(),
      accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
      accountCredentialsStore = this.credentialStore,
      accountsDatabases = this.accountsDatabases(),
      bookFormatSupport = BookFormatsTesting.supportsEverything,
      httpClient = httpClient, directory = fileProfiles
    )

    val acc = MockAccountProviders.fakeProvider("http://www.example.com/accounts0/")

    val p0 = db.currentProfile()
    p0.setDescription(
      ProfileDescription(
        p0.preferences().copy(dateOfBirth = ProfileDateOfBirth(DateTime(20L), true)),
        p0.attributes()
      )
    )

    Assertions.assertEquals(ProfileDateOfBirth(DateTime(20L), true), p0.preferences().dateOfBirth)
  }

  /**
   * Opening a profile database in anonymous mode works.
   */

  @Test
  @Throws(Exception::class)
  fun testAnonymous() {
    val fileTemp = DirectoryUtilities.directoryCreateTemporary()
    val fileProfiles = File(fileTemp, "profiles")

    val accountProviders =
      MockAccountProviders.fakeAccountProviders()

    val db0 =
      ProfilesDatabases.openWithAnonymousProfileEnabled(
        context = this.context(),
        analytics = this.analytics,
        accountEvents = this.accountEvents,
        accountProviders = accountProviders,
        accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
        accountCredentialsStore = this.credentialStore,
        accountsDatabases = this.accountsDatabases(),
        bookFormatSupport = BookFormatsTesting.supportsEverything,
        httpClient = httpClient, directory = fileProfiles
      )

    val p0 = db0.anonymousProfile()
    Assertions.assertEquals(p0, db0.currentProfile())
  }

  /**
   * Trying to delete an account with an unknown provider fails.
   */

  @Test
  @Throws(Exception::class)
  fun testDeleteUnknownProvider() {
    val fileTemp = DirectoryUtilities.directoryCreateTemporary()
    val fileProfiles = File(fileTemp, "profiles")

    val db0 = ProfilesDatabases.openWithAnonymousProfileEnabled(
      context = this.context(),
      analytics = this.analytics,
      accountEvents = this.accountEvents,
      accountProviders = MockAccountProviders.fakeAccountProviders(),
      accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
      accountCredentialsStore = this.credentialStore,
      accountsDatabases = this.accountsDatabases(),
      bookFormatSupport = BookFormatsTesting.supportsEverything,
      httpClient = httpClient, directory = fileProfiles
    )

    val acc0 =
      MockAccountProviders.fakeProvider("http://www.example.com/accounts0/")
    val acc1 =
      MockAccountProviders.fakeProvider("http://www.example.com/accounts1/")

    val p0 = db0.currentProfile()

    Assertions.assertThrows(
      AccountsDatabaseNonexistentException::class.java,
      Executable {
        p0.deleteAccountByProvider(acc1.id)
      }
    )
  }

  /**
   * If an account provider disappears, the profile database opens but the missing account
   * is not present.
   *
   * @throws Exception On errors
   */

  @Test
  @Throws(Exception::class)
  fun testOpenCreateReopenMissingAccountProvider() {
    val fileTemp = DirectoryUtilities.directoryCreateTemporary()
    val fileProfiles = File(fileTemp, "profiles")

    val accountProviderList =
      MockAccountProviders.fakeAccountProviderList()
    val accountProviders =
      MockAccountProviderRegistry.withProviders(accountProviderList)

    val db0 =
      ProfilesDatabases.openWithAnonymousProfileEnabled(
        context = this.context(),
        analytics = this.analytics,
        accountEvents = this.accountEvents,
        accountProviders = accountProviders,
        accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
        accountCredentialsStore = this.credentialStore,
        accountsDatabases = this.accountsDatabases(),
        bookFormatSupport = BookFormatsTesting.supportsEverything,
        httpClient = httpClient, directory = fileProfiles
      )

    val accountProvider0 =
      accountProviderList[0]
    val accountProvider1 =
      accountProviderList[1]

    val p0 = db0.currentProfile()
    p0.createAccount(accountProvider1)

    ProfilesDatabases.openWithAnonymousProfileEnabled(
      context = this.context(),
      analytics = this.analytics,
      accountEvents = this.accountEvents,
      accountProviders = MockAccountProviderRegistry.singleton(accountProvider0),
      accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
      accountCredentialsStore = this.credentialStore,
      accountsDatabases = this.accountsDatabases(),
      bookFormatSupport = BookFormatsTesting.supportsEverything,
      httpClient = httpClient, directory = fileProfiles
    )
  }

  /**
   * If an account provider disappears, and the profile only contained a single account that
   * has now disappeared, a new account is created.
   *
   * @throws Exception On errors
   */

  @Test
  @Throws(Exception::class)
  fun testOpenCreateReopenMissingAccountProviderNew() {
    val fileTemp = DirectoryUtilities.directoryCreateTemporary()
    val fileProfiles = File(fileTemp, "profiles")

    val accountProviderList =
      MockAccountProviders.fakeAccountProviderList()
    val accountProviders =
      MockAccountProviderRegistry.withProviders(accountProviderList)
    val accountProvidersOnly1 =
      MockAccountProviderRegistry.singleton(accountProviderList[1])

    val db0 =
      ProfilesDatabases.openWithAnonymousProfileEnabled(
        context = this.context(),
        analytics = this.analytics,
        accountEvents = this.accountEvents,
        accountProviders = accountProviders,
        accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
        accountCredentialsStore = this.credentialStore,
        accountsDatabases = this.accountsDatabases(),
        bookFormatSupport = BookFormatsTesting.supportsEverything,
        httpClient = httpClient, directory = fileProfiles
      )

    val p0 =
      db0.currentProfile()

    val db1 =
      ProfilesDatabases.openWithAnonymousProfileEnabled(
        context = this.context(),
        analytics = this.analytics,
        accountEvents = this.accountEvents,
        accountProviders = accountProvidersOnly1,
        accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
        accountCredentialsStore = this.credentialStore,
        accountsDatabases = this.accountsDatabases(),
        bookFormatSupport = BookFormatsTesting.supportsEverything,
        httpClient = httpClient, directory = fileProfiles
      )

    val p0After = db1.currentProfile()
    Assertions.assertEquals(1, p0After.accounts().size)
  }

  /**
   * Repeatedly reopening a database in anonymous/non-anonymous mode doesn't cause any damage.
   *
   * @throws Exception On errors
   */

  @Test
  @Throws(Exception::class)
  fun testOpenAnonymousNonAnonymousAlternating() {
    val fileTemp = DirectoryUtilities.directoryCreateTemporary()
    val fileProfiles = File(fileTemp, "profiles")

    val accountProviders =
      MockAccountProviders.fakeAccountProviders()

    val db0 =
      ProfilesDatabases.openWithAnonymousProfileEnabled(
        context = this.context(),
        analytics = this.analytics,
        accountEvents = this.accountEvents,
        accountProviders = accountProviders,
        accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
        accountCredentialsStore = this.credentialStore,
        accountsDatabases = this.accountsDatabases(),
        bookFormatSupport = BookFormatsTesting.supportsEverything,
        httpClient = httpClient, directory = fileProfiles
      )

    val acc =
      MockAccountProviders.fakeProvider("urn:fake:0")
    val p0 =
      db0.currentProfile()
    val acc0 =
      p0.createAccount(MockAccountProviders.fakeProvider("urn:fake:1"))

    val db1 =
      ProfilesDatabases.openWithAnonymousProfileEnabled(
        context = this.context(),
        analytics = this.analytics,
        accountEvents = this.accountEvents,
        accountProviders = accountProviders,
        accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
        accountCredentialsStore = this.credentialStore,
        accountsDatabases = this.accountsDatabases(),
        bookFormatSupport = BookFormatsTesting.supportsEverything,
        httpClient = httpClient, directory = fileProfiles
      )

    val p1 = db1.anonymousProfile()

    Assertions.assertTrue(p0.accounts().containsKey(acc0.id))
    Assertions.assertTrue(p0.accounts().containsKey(acc0.id))
  }

  /**
   * If the "most recent account" ID refers to an account that doesn't exist, then it must
   * be wiped out when the profiles database is opened.
   *
   * @throws Exception On errors
   */

  @Test
  @Throws(Exception::class)
  fun testInvalidMostRecent() {
    val f_tmp = DirectoryUtilities.directoryCreateTemporary()
    val f_pro = File(f_tmp, "profiles")

    val accountProviders =
      MockAccountProviders.fakeAccountProviders()

    val db0 =
      ProfilesDatabases.openWithAnonymousProfileEnabled(
        context = this.context(),
        analytics = this.analytics,
        accountEvents = this.accountEvents,
        accountProviders = accountProviders,
        accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
        accountCredentialsStore = this.credentialStore,
        accountsDatabases = this.accountsDatabases(),
        bookFormatSupport = BookFormatsTesting.supportsEverything,
        httpClient = httpClient, directory = f_pro
      )

    val pro0 = db0.currentProfile()

    // Palace automatically deletes the default account, so we need to create an initial account.
    val acc0p = MockAccountProviders.fakeProvider("urn:fake:1")
    val acc0 = pro0.createAccount(acc0p)

    val acc1p = MockAccountProviders.fakeProvider("urn:fake:2")
    val acc1 = pro0.createAccount(acc1p)

    val pro0desc =
      pro0.description()
    val pro0descNew =
      pro0desc.copy(preferences = pro0desc.preferences.copy(mostRecentAccount = acc1.id))

    pro0.setDescription(pro0descNew)

    /*
     * Delete the account on disk without going through the proper channels.
     */

    val f_account =
      File(File(File(f_pro, pro0.id.uuid.toString()), "accounts"), acc1.id.uuid.toString())

    this.logger.debug("deleting account {}", acc1.id.uuid)
    f_account.deleteRecursively()

    val db1 =
      ProfilesDatabases.openWithAnonymousProfileEnabled(
        context = this.context(),
        analytics = this.analytics,
        accountEvents = this.accountEvents,
        accountProviders = accountProviders,
        accountBundledCredentials = AccountBundledCredentialsEmpty.getInstance(),
        accountCredentialsStore = this.credentialStore,
        accountsDatabases = this.accountsDatabases(),
        bookFormatSupport = BookFormatsTesting.supportsEverything,
        httpClient = httpClient, directory = f_pro
      )

    val pro1 = db1.currentProfile()
    pro1.account(pro1.preferences().mostRecentAccount)
  }
}
