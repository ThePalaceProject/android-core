package org.nypl.simplified.tests.db

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParser
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.opds2.irradia.OPDS2ParsersIrradia
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQSchemaVersionType
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
      AccountProviderDescriptionCollectionParsers(OPDS2ParsersIrradia)
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
  fun testSchemaVersion() {
    this.database.openConnection().use { c ->
      c.openTransaction().use { t ->
        val q = t.query(DBQSchemaVersionType::class.java)
        Assertions.assertEquals(1L, q.execute(t, Unit))
      }
    }
  }
}

