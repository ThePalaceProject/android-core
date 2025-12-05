package org.nypl.simplified.tests.books.accounts

import android.content.Context
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountProviderResolutionListenerType
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.accounts.registry.AccountProviderRegistry2
import org.nypl.simplified.accounts.source.spi.AccountProviderSourceType
import org.nypl.simplified.opds2.irradia.OPDS2ParsersIrradia
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.tests.mocking.MockAccountProviders
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionGetType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionPutType
import org.thepalaceproject.db.api.queries.DBQAccountProviderGetType
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AccountProviderRegistry2Test {

  private lateinit var mockContext: Context
  private lateinit var dbExecutor: ExecutorService
  private lateinit var accountProviders: List<AccountProvider>
  private lateinit var accountProvider: AccountProvider
  private lateinit var database: DBType
  private lateinit var databaseFile: Path
  private lateinit var directory: Path

  @BeforeEach
  fun setup(@TempDir directory: Path) {
    this.mockContext = Mockito.mock<Context>()
    this.directory = directory
    this.databaseFile = directory.resolve("database.db")
    this.database = DBFactory.open(
      DBParameters(
        file =
          this.databaseFile,
        accountProviderParsers =
          AccountProviderDescriptionCollectionParsers(OPDS2ParsersIrradia),
        accountProviderSerializers =
          AccountProviderDescriptionCollectionSerializers()
      )
    )
    this.accountProviders =
      MockAccountProviders.fakeAccountProviderList()
    this.accountProvider =
      this.accountProviders[0]
    this.dbExecutor =
      Executors.newSingleThreadExecutor()
  }

  @AfterEach
  fun tearDown() {
    this.database.close()
    this.dbExecutor.shutdown()
  }

  @Test
  fun testOpenUpdateDescription() {
    this.writeAccountProviders()

    val r =
      AccountProviderRegistry2.create(
        attributeExecutor = { r -> r.run() },
        context = this.mockContext,
        database = this.database,
        databaseExecutor = this.dbExecutor,
        defaultProvider = this.accountProvider,
        sources = listOf(),
      )

    r.updateDescription(this.accountProviders[0].toDescription())

    assertProviderDescriptionExists(this.accountProviders[0].id)
    assertProviderDescriptionExists(this.accountProviders[1].id)
    assertProviderDescriptionExists(this.accountProviders[2].id)
    assertProviderDescriptionExists(this.accountProviders[3].id)
  }

  @Test
  fun testOpenUpdateProvider() {
    this.writeAccountProviders()

    val r =
      AccountProviderRegistry2.create(
        attributeExecutor = { r -> r.run() },
        context = this.mockContext,
        database = this.database,
        databaseExecutor = this.dbExecutor,
        defaultProvider = this.accountProvider,
        sources = listOf(),
      )

    assertProviderExists(this.accountProviders[0].id)
    assertProviderNonexistent(this.accountProviders[1].id)
    assertProviderNonexistent(this.accountProviders[2].id)
    assertProviderNonexistent(this.accountProviders[3].id)

    r.updateProvider(this.accountProviders[1])

    assertProviderExists(this.accountProviders[0].id)
    assertProviderExists(this.accountProviders[1].id)
    assertProviderNonexistent(this.accountProviders[2].id)
    assertProviderNonexistent(this.accountProviders[3].id)

    assertProviderDescriptionExists(this.accountProviders[0].id)
    assertProviderDescriptionExists(this.accountProviders[1].id)
    assertProviderDescriptionExists(this.accountProviders[2].id)
    assertProviderDescriptionExists(this.accountProviders[3].id)

    r.updateProvider(this.accountProviders[1])

    assertProviderExists(this.accountProviders[0].id)
    assertProviderExists(this.accountProviders[1].id)
    assertProviderNonexistent(this.accountProviders[2].id)
    assertProviderNonexistent(this.accountProviders[3].id)

    assertProviderDescriptionExists(this.accountProviders[0].id)
    assertProviderDescriptionExists(this.accountProviders[1].id)
    assertProviderDescriptionExists(this.accountProviders[2].id)
    assertProviderDescriptionExists(this.accountProviders[3].id)
  }

  @Test
  fun testRefresh() {
    this.writeAccountProviders()

    val source =
      StaticSource(
        listOf(
          this.accountProviders[1].toDescription(),
          this.accountProviders[2].toDescription()
        )
      )

    val r =
      AccountProviderRegistry2.create(
        attributeExecutor = { r -> r.run() },
        context = this.mockContext,
        database = this.database,
        databaseExecutor = this.dbExecutor,
        defaultProvider = this.accountProvider,
        sources = listOf(source),
      )

    r.refresh(
      includeTestingLibraries = true,
    )

    assertProviderDescriptionExists(this.accountProviders[0].id)
    assertProviderDescriptionExists(this.accountProviders[1].id)
    assertProviderDescriptionExists(this.accountProviders[2].id)
    assertProviderDescriptionNonexistent(this.accountProviders[3].id)

    r.clear()

    assertProviderDescriptionExists(this.accountProviders[0].id)
    assertProviderDescriptionNonexistent(this.accountProviders[1].id)
    assertProviderDescriptionNonexistent(this.accountProviders[2].id)
    assertProviderDescriptionNonexistent(this.accountProviders[3].id)
  }

  private fun assertProviderDescriptionExists(id: URI) {
    Assertions.assertNotEquals(
      null,
      this.database.openTransaction().use { t ->
        t.execute(DBQAccountProviderDescriptionGetType::class.java, id)
      },
      "Provider $id must exist"
    )
  }

  private fun assertProviderDescriptionNonexistent(id: URI) {
    Assertions.assertEquals(
      null,
      this.database.openTransaction().use { t ->
        t.execute(DBQAccountProviderDescriptionGetType::class.java, id)
      },
      "Provider $id must not exist"
    )
  }

  private fun assertProviderExists(id: URI) {
    Assertions.assertNotEquals(
      null,
      this.database.openTransaction().use { t ->
        t.execute(DBQAccountProviderGetType::class.java, id)
      },
      "Provider $id must exist"
    )
  }

  private fun assertProviderNonexistent(id: URI) {
    Assertions.assertEquals(
      null,
      this.database.openTransaction().use { t ->
        t.execute(DBQAccountProviderGetType::class.java, id)
      },
      "Provider $id must not exist"
    )
  }

  private class StaticSource(
    private val providers: List<AccountProviderDescription>
  ) : AccountProviderSourceType {

    override fun load(
      context: Context,
      includeTestingLibraries: Boolean,
    ): AccountProviderSourceType.SourceResult {
      return AccountProviderSourceType.SourceResult.SourceSucceeded(
        this.providers.associateBy { d -> d.id }
      )
    }

    override fun canResolve(description: AccountProviderDescription): Boolean {
      TODO("Not yet implemented")
    }

    override fun resolve(
      onProgress: AccountProviderResolutionListenerType,
      description: AccountProviderDescription
    ): TaskResult<AccountProviderType> {
      TODO("Not yet implemented")
    }
  }

  private fun writeAccountProviders() {
    this.database.openTransaction().use { t ->
      t.execute(DBQAccountProviderDescriptionPutType::class.java, this.accountProviders.map { d -> d.toDescription() })
      t.commit()
    }
  }
}
