package org.nypl.simplified.tests.db

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.nypl.simplified.accounts.api.AccountProvider
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionParsers
import org.nypl.simplified.accounts.json.AccountProviderDescriptionCollectionSerializers
import org.nypl.simplified.opds2.irradia.OPDS2ParsersIrradia
import org.nypl.simplified.tests.mocking.MockAccountProviders
import org.thepalaceproject.db.DBFactory
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionDeleteType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionGetType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionIDSetType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionListType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionPutType
import org.thepalaceproject.db.api.queries.DBQAccountProviderGetType
import org.thepalaceproject.db.api.queries.DBQAccountProviderListType
import org.thepalaceproject.db.api.queries.DBQAccountProviderPutType
import java.net.URI
import java.nio.file.Path
import java.util.UUID

class DBAccountTest {

  private lateinit var accountSerializers: AccountProviderDescriptionCollectionSerializers
  private lateinit var accountParsers: AccountProviderDescriptionCollectionParsers
  private lateinit var database: DBType
  private lateinit var directory: Path
  private lateinit var file: Path

  @BeforeEach
  fun setup(@TempDir directory: Path) {
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
  fun tearDown() {
    this.database.close()
  }

  @Test
  fun testAccountProviderDescriptionGetNone() {
    this.database.openTransaction().use { t ->
      val query =
        t.query(DBQAccountProviderDescriptionGetType::class.java)

      Assertions.assertEquals(
        null,
        query.execute(t, URI.create("urn:any"))
      )
    }
  }

  @Test
  fun testAccountProviderDescriptionGetNoneExecute() {
    this.database.openTransaction().use { t ->
      Assertions.assertEquals(
        null,
        t.execute(DBQAccountProviderDescriptionGetType::class.java, URI.create("urn:any"))
      )
    }
  }

  @Test
  fun testAccountProviderDescriptionPutGet() {
    this.database.openTransaction().use { t ->

      val w =
        AccountProviderDescription(
          id = URI.create("urn:uuid:c7b2959b-8986-49d6-90d6-d4057a3b2900"),
          title = "Example Library",
          description = "",
          updated = DateTime.now(DateTimeZone.UTC),
          links = listOf(),
          images = listOf(),
          isAutomatic = false,
          isProduction = true,
          location = null
        )

      t.execute(DBQAccountProviderDescriptionPutType::class.java, w)
      t.commit()

      val r =
        t.execute(DBQAccountProviderDescriptionGetType::class.java, w.id)!!

      Assertions.assertEquals(w.authenticationDocumentURI, r.authenticationDocumentURI)
      Assertions.assertEquals(w.catalogURI, r.catalogURI)
      Assertions.assertEquals(w.description, r.description)
      Assertions.assertEquals(w.id, r.id)
      Assertions.assertEquals(w.images, r.images)
      Assertions.assertEquals(w.isAutomatic, r.isAutomatic)
      Assertions.assertEquals(w.isProduction, r.isProduction)
      Assertions.assertEquals(w.links, r.links)
      Assertions.assertEquals(w.location, r.location)
      Assertions.assertEquals(w.logoURI, r.logoURI)
      Assertions.assertEquals(w.updated, r.updated)
      Assertions.assertEquals(w, r)
    }
  }

  @Test
  fun testAccountProviderDescriptionPutList() {
    this.database.openTransaction().use { t ->

      val inputs = mutableListOf<AccountProviderDescription>()
      for (i in 0 until 100) {
        inputs.add(
          AccountProviderDescription(
            id = URI.create("urn:uuid:${UUID.randomUUID()}"),
            title = "Example Library $i",
            description = "",
            updated = DateTime.now(DateTimeZone.UTC),
            links = listOf(),
            images = listOf(),
            isAutomatic = false,
            isProduction = true,
            location = null
          )
        )
      }
      inputs.sortBy { description -> description.id }

      for (input in inputs) {
        t.execute(DBQAccountProviderDescriptionPutType::class.java, input)
      }
      t.commit()

      run {
        val r =
          t.execute(
            DBQAccountProviderDescriptionListType::class.java,
            DBQAccountProviderDescriptionListType.Parameters(
              startingId = null,
              limit = 50
            )
          )

        for (i in 0 until 50) {
          Assertions.assertEquals(inputs[i], r[i])
        }
      }

      run {
        val r =
          t.execute(
            DBQAccountProviderDescriptionListType::class.java,
            DBQAccountProviderDescriptionListType.Parameters(
              startingId = inputs[20].id,
              limit = 10
            )
          )

        for (i in 21 until 21 + 10) {
          Assertions.assertEquals(inputs[i], r[i - 21])
        }
      }
    }
  }

  @Test
  fun testAccountProviderDescriptionPutDelete() {
    this.database.openTransaction().use { t ->

      val inputs = mutableListOf<AccountProviderDescription>()
      for (i in 0 until 100) {
        inputs.add(
          AccountProviderDescription(
            id = URI.create("urn:uuid:${UUID.randomUUID()}"),
            title = "Example Library $i",
            description = "",
            updated = DateTime.now(DateTimeZone.UTC),
            links = listOf(),
            images = listOf(),
            isAutomatic = false,
            isProduction = true,
            location = null
          )
        )
      }
      inputs.sortBy { description -> description.id }

      for (input in inputs) {
        t.execute(DBQAccountProviderDescriptionPutType::class.java, input)
      }
      t.commit()

      t.execute(
        DBQAccountProviderDescriptionDeleteType::class.java,
        inputs.map { d -> d.id }.take(50).toSet()
      )
      t.commit()

      for (i in 0 until 100) {
        if (i < 50) {
          Assertions.assertEquals(
            null,
            t.execute(DBQAccountProviderDescriptionGetType::class.java, inputs[i].id)
          )
        } else {
          Assertions.assertNotEquals(
            null,
            t.execute(DBQAccountProviderDescriptionGetType::class.java, inputs[i].id)
          )
        }
      }
    }
  }

  @Test
  fun testAccountProviderDescriptionIDSet() {
    this.database.openTransaction().use { t ->

      val inputs = mutableListOf<AccountProviderDescription>()
      for (i in 0 until 100) {
        inputs.add(
          AccountProviderDescription(
            id = URI.create("urn:uuid:${UUID.randomUUID()}"),
            title = "Example Library $i",
            description = "",
            updated = DateTime.now(DateTimeZone.UTC),
            links = listOf(),
            images = listOf(),
            isAutomatic = false,
            isProduction = true,
            location = null
          )
        )
      }
      inputs.sortBy { description -> description.id }

      for (input in inputs) {
        t.execute(DBQAccountProviderDescriptionPutType::class.java, input)
      }
      t.commit()

      val receivedIds =
        t.execute(DBQAccountProviderDescriptionIDSetType::class.java, Unit)
      val expectedIDs =
        inputs.map { d -> d.id }.toSet()

      Assertions.assertEquals(
        expectedIDs,
        receivedIds
      )
    }
  }

  @Test
  fun testAccountProviderPutGet() {
    this.database.openTransaction().use { t ->
      val w = MockAccountProviders.fakeAccountProviderList()[0] as AccountProvider

      t.execute(DBQAccountProviderPutType::class.java, w)
      t.commit()

      val r = t.execute(DBQAccountProviderGetType::class.java, w.id)!!
      Assertions.assertEquals(w, r)
    }
  }

  @Test
  fun testAccountProviderPutList() {
    this.database.openTransaction().use { t ->

      val inputs = mutableListOf<AccountProvider>()
      for (i in 0 until 100) {
        inputs.add(
          MockAccountProviders.fakeProvider(
            providerId = "urn:uuid:${UUID.randomUUID()}"
          )
        )
      }
      inputs.sortBy { description -> description.id }

      for (input in inputs) {
        t.execute(DBQAccountProviderPutType::class.java, input)
      }
      t.commit()

      run {
        val r =
          t.execute(
            DBQAccountProviderListType::class.java,
            DBQAccountProviderListType.Parameters(
              startingId = null,
              limit = 50
            )
          )

        for (i in 0 until 50) {
          Assertions.assertEquals(inputs[i], r[i])
        }
      }

      run {
        val r =
          t.execute(
            DBQAccountProviderListType::class.java,
            DBQAccountProviderListType.Parameters(
              startingId = inputs[20].id,
              limit = 10
            )
          )

        for (i in 21 until 21 + 10) {
          Assertions.assertEquals(inputs[i], r[i - 21])
        }
      }
    }
  }
}

