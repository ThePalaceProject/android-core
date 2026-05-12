package org.nypl.simplified.tests.db

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionListType
import org.thepalaceproject.db.api.queries.DBQSchemaVersionType
import java.nio.file.Files
import java.nio.file.Path

class DBTest {

  private lateinit var accountSerializers: AccountProviderDescriptionCollectionSerializers
  private lateinit var accountParsers: AccountProviderDescriptionCollectionParsers
  private lateinit var database: DBType
  private lateinit var directory: Path
  private lateinit var file: Path

  @BeforeEach
  fun setup(@TempDir directory: Path)
  {
    this.directory = directory
    this.file = directory.resolve("database.db")

    this.accountParsers =
      AccountProviderDescriptionCollectionParsers()
    this.accountSerializers =
      AccountProviderDescriptionCollectionSerializers()

    this.database = DBFactory.open(
      DBParameters(
        file = this.file,
        accountProviderParsers = this.accountParsers,
        accountProviderSerializers = this.accountSerializers
      )
    )
  }

  @AfterEach
  fun tearDown()
  {
    this.database.close()
  }

  @Test
  fun testOpenClose() {
    // Nothing required.
  }

  @Test
  fun testOpenTransactionNoOp() {
    this.database.openConnection().use { c ->
      c.openTransaction().use { t ->

      }
    }
  }

  @Test
  fun testOpenCopyProviders(
    @TempDir directory: Path
  ) {
    val providers =
      DBTest::class.java.getResourceAsStream("/org/nypl/simplified/tests/db/providers.db")
    val inputFile =
      directory.resolve("out.db")

    Files.copy(providers, inputFile)

    this.database.copyAccountProviderDescriptionsFrom(inputFile)
    this.database.openConnection().use { c ->
      c.openTransaction().use { t ->
        assertEquals(
          1134,
          t.execute(DBQAccountProviderDescriptionListType::class.java,
          DBQAccountProviderDescriptionListType.Parameters(null, 10000)).size)
      }
    }
  }

  @Test
  fun testSchemaVersion() {
    this.database.openConnection().use { c ->
      c.openTransaction().use { t ->
        val q = t.query(DBQSchemaVersionType::class.java)
        Assertions.assertEquals(2L, q.execute(t, Unit))
      }
    }
  }
}

