package org.nypl.simplified.tests.mdm

import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FluentFuture
import com.google.common.util.concurrent.SettableFuture
import io.reactivex.Observable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.feeds.api.FeedData
import org.nypl.simplified.profiles.api.ProfileAttributes
import org.nypl.simplified.profiles.api.ProfileDescription
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfilePreferences
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.profiles.api.ProfileUpdated
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfileFeedRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.reader.api.ReaderPreferences
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.taskrecorder.api.TaskStepResolution
import org.nypl.simplified.tests.TestDirectories
import org.nypl.simplified.tests.mocking.MockAccount
import org.thepalaceproject.mdm.MDMConfiguration
import org.thepalaceproject.mdm.MDMConfigurationInvalidField
import org.thepalaceproject.mdm.MDMConfigurationParseResult
import org.thepalaceproject.mdm.MDMLibraryApplier
import org.thepalaceproject.palace.diagnostics.api.DiagnosticCodes
import org.thepalaceproject.palace.diagnostics.api.DiagnosticsServiceType
import java.io.File
import java.net.URI
import java.util.SortedMap
import java.util.UUID

class MDMLibraryApplierTest {

  private val libraryA =
    URI.create("urn:uuid:00000000-0000-4000-8000-000000000000")
  private val libraryB =
    URI.create("urn:uuid:00000000-0000-4000-8000-000000000001")

  @Test
  fun disabledFlagNoApplication() {
    val controller =
      this.newController(mdmEnabled = false)
    val diagnostics =
      FakeDiagnosticsService()

    MDMLibraryApplier(controller, diagnostics)
      .apply(this.resultWith(this.libraryA, this.libraryB))

    assertTrue(controller.createdProviders.isEmpty())
    assertTrue(diagnostics.reports.isEmpty())
  }

  @Test
  fun disabledFlagIgnoresInvalidFields() {
    val controller =
      this.newController(mdmEnabled = false)
    val diagnostics =
      FakeDiagnosticsService()

    MDMLibraryApplier(controller, diagnostics)
      .apply(
        MDMConfigurationParseResult(
          configuration = MDMConfiguration(listOf()),
          invalidFields = listOf(MDMConfigurationInvalidField("Library-3", "not-a-uuid")),
        ),
      )

    assertTrue(diagnostics.reports.isEmpty())
  }

  @Test
  fun enabledFlagCreatesMissingLibraries() {
    val controller =
      this.newController(mdmEnabled = true)

    MDMLibraryApplier(controller, FakeDiagnosticsService())
      .apply(this.resultWith(this.libraryA, this.libraryB))

    assertEquals(listOf(this.libraryA, this.libraryB), controller.createdProviders)
  }

  @Test
  fun enabledFlagDoesNotRecreateExistingLibraries() {
    val controller =
      this.newController(
        mdmEnabled = true,
        existingAccounts = mapOf(this.libraryA to this.newAccount()),
      )

    MDMLibraryApplier(controller, FakeDiagnosticsService())
      .apply(this.resultWith(this.libraryA, this.libraryB))

    assertEquals(listOf(this.libraryB), controller.createdProviders)
  }

  @Test
  fun enabledFlagReapplicationIsIdempotent() {
    val controller =
      this.newController(mdmEnabled = true)
    val applier =
      MDMLibraryApplier(controller, FakeDiagnosticsService())

    applier.apply(this.resultWith(this.libraryA, this.libraryB))
    applier.apply(this.resultWith(this.libraryA, this.libraryB))

    assertEquals(listOf(this.libraryA, this.libraryB), controller.createdProviders)
  }

  @Test
  fun enabledFlagSelectsFirstConfiguredLibrary() {
    val controller =
      this.newController(
        mdmEnabled = true,
        existingAccounts =
          mapOf(
            this.libraryA to this.newAccount(),
            this.libraryB to this.newAccount(),
          ),
      )
    val accountB =
      controller.profile.accountsByProvider()[this.libraryB]!!

    MDMLibraryApplier(controller, FakeDiagnosticsService())
      .apply(this.resultWith(this.libraryB, this.libraryA))

    assertEquals(accountB.id, controller.profile.description.preferences.mostRecentAccount)
  }

  @Test
  fun enabledFlagSelectsFirstAvailableConfiguredLibrary() {
    val controller =
      this.newController(
        mdmEnabled = true,
        existingAccounts = mapOf(this.libraryB to this.newAccount()),
      )
    controller.createFailures[this.libraryA] =
      IllegalStateException("unknown provider")
    val accountB =
      controller.profile.accountsByProvider()[this.libraryB]!!

    MDMLibraryApplier(controller, FakeDiagnosticsService())
      .apply(this.resultWith(this.libraryA, this.libraryB))

    assertEquals(accountB.id, controller.profile.description.preferences.mostRecentAccount)
  }

  @Test
  fun enabledFlagSelectsNewlyCreatedLibrary() {
    val controller =
      this.newController(mdmEnabled = true)

    MDMLibraryApplier(controller, FakeDiagnosticsService())
      .apply(this.resultWith(this.libraryA))

    val accountA =
      controller.profile.accountsByProvider()[this.libraryA]
    assertEquals(accountA!!.id, controller.profile.description.preferences.mostRecentAccount)
  }

  @Test
  fun enabledFlagReportsInvalidFields() {
    val controller =
      this.newController(mdmEnabled = true)
    val diagnostics =
      FakeDiagnosticsService()

    MDMLibraryApplier(controller, diagnostics)
      .apply(
        MDMConfigurationParseResult(
          configuration = MDMConfiguration(listOf()),
          invalidFields = listOf(MDMConfigurationInvalidField("Library-3", "not-a-uuid")),
        ),
      )

    assertEquals(1, diagnostics.reports.size)
    val (code, data, _) =
      diagnostics.reports[0]
    assertEquals(DiagnosticCodes.MDM_CONFIGURATION_INVALID_FIELD, code)
    assertEquals(mapOf("Field" to "Library-3", "Value" to "not-a-uuid"), data)
  }

  @Test
  fun enabledFlagReportsCreateFailures() {
    val controller =
      this.newController(mdmEnabled = true)
    controller.createFailures[this.libraryA] =
      IllegalStateException("nope")
    val diagnostics =
      FakeDiagnosticsService()

    MDMLibraryApplier(controller, diagnostics)
      .apply(this.resultWith(this.libraryA, this.libraryB))

    assertEquals(1, diagnostics.reports.size)
    val (code, data, throwable) =
      diagnostics.reports[0]
    assertEquals(DiagnosticCodes.MDM_LIBRARY_CREATE_FAILED, code)
    assertEquals(
      mapOf(
        "Provider" to this.libraryA.toString(),
        "ExceptionType" to "java.lang.IllegalStateException",
      ),
      data,
    )
    assertEquals(IllegalStateException::class.java, throwable?.javaClass)
  }

  private fun newController(
    mdmEnabled: Boolean,
    existingAccounts: Map<URI, AccountType> = mapOf(),
  ): FakeProfilesController =
    FakeProfilesController(
      initialDescription =
        this.description(
          enableMDMLibraries = mdmEnabled,
          mostRecentAccount = AccountID(UUID.randomUUID()),
        ),
      existingAccounts = existingAccounts,
    )

  private fun newAccount(): AccountType =
    MockAccount(TestDirectories.temporaryDirectory(), AccountID(UUID.randomUUID()))

  private fun resultWith(vararg uris: URI): MDMConfigurationParseResult =
    MDMConfigurationParseResult(
      configuration = MDMConfiguration(uris.toList()),
      invalidFields = listOf(),
    )

  private fun description(
    enableMDMLibraries: Boolean,
    mostRecentAccount: AccountID,
  ): ProfileDescription =
    ProfileDescription(
      preferences =
        ProfilePreferences(
          dateOfBirth = null,
          showTestingLibraries = false,
          hasSeenLibrarySelectionScreen = false,
          hasSeenNotificationScreen = false,
          readerPreferences = ReaderPreferences.builder().build(),
          playbackRates = mapOf(),
          mostRecentAccount = mostRecentAccount,
          lastAudiobook = null,
          showDebugSettings = false,
          downloadOnlyOnWIFI = false,
          isLCPManualPassphraseEnabled = false,
          audioBookPlaybackSkipIntervalForwardMs = 30_000L,
          audioBookPlaybackSkipIntervalBackwardMs = 30_000L,
          audioBookBatteryDialogAccepted = false,
          showBookCoverDebugLabels = false,
          preferOPDS20 = false,
          enableMDMLibraries = enableMDMLibraries,
        ),
      attributes = ProfileAttributes(sortedMapOf()),
    )

  private class FakeDiagnosticsService : DiagnosticsServiceType {
    val reports =
      mutableListOf<Triple<String, Map<String, String>, Throwable?>>()

    override fun report(
      code: String,
      data: Map<String, String>,
      throwable: Throwable?,
    ) {
      this.reports.add(Triple(code, data, throwable))
    }
  }

  private class FakeProfile(
    var description: ProfileDescription,
    val accounts: MutableMap<URI, AccountType>,
  ) : ProfileReadableType {
    override val id: ProfileID =
      ProfileID(UUID.randomUUID())

    override val directory: File =
      TestDirectories.temporaryDirectory()

    override fun description(): ProfileDescription =
      this.description

    override fun accounts(): SortedMap<AccountID, AccountType> {
      val accounts =
        sortedMapOf<AccountID, AccountType>()
      for (account in this.accounts.values) {
        accounts[account.id] = account
      }
      return accounts
    }

    override fun accountsByProvider(): SortedMap<URI, AccountType> =
      this.accounts.toSortedMap()

    override fun account(accountId: AccountID): AccountType =
      this.accounts.values.first { it.id == accountId }

    override fun compareTo(other: ProfileReadableType): Int =
      this.id.compareTo(other.id)
  }

  private class FakeProfilesController(
    private val initialDescription: ProfileDescription,
    existingAccounts: Map<URI, AccountType>,
  ) : ProfilesControllerType {
    val createdProviders =
      mutableListOf<URI>()
    val createFailures =
      mutableMapOf<URI, Throwable>()
    val profile =
      FakeProfile(
        description = this.initialDescription,
        accounts = existingAccounts.toMutableMap(),
      )

    override fun profiles(): SortedMap<ProfileID, ProfileReadableType> =
      sortedMapOf(
        Pair(
          this.profile.id,
          this.profile as ProfileReadableType,
        ),
      )

    override fun profileCurrent(): ProfileReadableType =
      this.profile

    override fun profileEvents(): Observable<ProfileEvent> =
      Observable.never()

    override fun profileAccountLogin(request: ProfileAccountLoginRequest): FluentFuture<TaskResult<Unit>> {
      TODO("not needed")
    }

    override fun profileAccountCreate(provider: URI): FluentFuture<TaskResult<AccountType>> {
      this.createdProviders.add(provider)
      val future =
        SettableFuture.create<TaskResult<AccountType>>()
      val failure =
        this.createFailures[provider]
      if (failure == null) {
        val account =
          MockAccount(TestDirectories.temporaryDirectory(), AccountID(UUID.randomUUID()))
        this.profile.accounts[provider] = account
        future.set(this.successfulResult(account))
      } else {
        future.set(this.failedResult(failure))
      }
      return FluentFuture.from(future)
    }

    private fun successfulResult(account: AccountType): TaskResult.Success<AccountType> =
      TaskResult.Success(
        result = account,
        steps =
          listOf(
            TaskStep(
              description = "test",
              resolution = TaskStepResolution.TaskStepSucceeded("test"),
            ),
          ),
        attributes = mapOf(),
      )

    private fun failedResult(throwable: Throwable): TaskResult.Failure<AccountType> =
      TaskResult.Failure(
        steps =
          listOf(
            TaskStep(
              description = "test",
              resolution =
                TaskStepResolution.TaskStepFailed(
                  message = "test",
                  exception = throwable,
                  errorCode = "test",
                  extraMessages = listOf(),
                ),
            ),
          ),
        attributes = mapOf(),
      )

    override fun profileAccountCreateCustomOPDS(opdsFeed: URI): FluentFuture<TaskResult<AccountType>> {
      TODO("not needed")
    }

    override fun profileAccountCreateOrReturnExisting(provider: URI): FluentFuture<TaskResult<AccountType>> {
      TODO("not needed")
    }

    override fun profileAccountDeleteByProvider(provider: URI): FluentFuture<TaskResult<Unit>> {
      TODO("not needed")
    }

    override fun profileAccountFindByProvider(provider: URI): AccountType {
      TODO("not needed")
    }

    override fun accountEvents(): Observable<AccountEvent> =
      Observable.never()

    override fun profileCurrentlyUsedAccountProviders(): ImmutableList<AccountProviderType> =
      ImmutableList.of()

    override fun profileAccountLogout(accountID: AccountID): FluentFuture<TaskResult<Unit>> {
      TODO("not needed")
    }

    override fun profileUpdate(update: (ProfileDescription) -> ProfileDescription): FluentFuture<ProfileUpdated> {
      val oldDescription =
        this.profile.description
      val newDescription =
        update(oldDescription)
      this.profile.description = newDescription
      val future =
        SettableFuture.create<ProfileUpdated>()
      future.set(ProfileUpdated.Succeeded(oldDescription, newDescription))
      return FluentFuture.from(future)
    }

    override fun profileFeed(request: ProfileFeedRequest): FluentFuture<FeedData> {
      TODO("not needed")
    }

    override fun profileAccountForBook(bookID: BookID): AccountType {
      TODO("not needed")
    }

    override fun profileAccountAdobeIDReset(id: AccountID): FluentFuture<TaskResult<*>> {
      TODO("not needed")
    }
  }
}
