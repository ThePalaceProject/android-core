package org.thepalaceproject.db.internal

import com.io7m.anethum.api.ParseStatus
import com.io7m.jxe.core.JXEHardenedSAXParsers
import com.io7m.trasco.api.TrArgumentType
import com.io7m.trasco.api.TrArguments
import com.io7m.trasco.api.TrEventExecutingSQL
import com.io7m.trasco.api.TrEventType
import com.io7m.trasco.api.TrEventUpgrading
import com.io7m.trasco.api.TrExecutorConfiguration
import com.io7m.trasco.api.TrExecutorUpgrade
import com.io7m.trasco.api.TrSchemaRevisionSet
import com.io7m.trasco.vanilla.TrExecutors
import com.io7m.trasco.vanilla.TrSchemaRevisionSetParsers
import org.apache.xerces.jaxp.SAXParserFactoryImpl
import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollectionParsersType
import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollectionSerializersType
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteOpenMode
import org.thepalaceproject.db.api.DBConnectionType
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBQueryType
import org.thepalaceproject.db.api.DBTransactionCloseBehavior
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.api.DBType
import java.io.IOException
import java.math.BigInteger
import java.net.URI
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.util.Map
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Supplier

internal class DBInstance private constructor(
  private val dataSource: SQLiteDataSource,
  private val parameters: DBParameters
) : DBType {

  private val closed = AtomicBoolean(false)

  private val services =
    mapOf<Class<*>, Any>(
      Pair(
        AccountProviderDescriptionCollectionParsersType::class.java,
        this.parameters.accountProviderParsers
      ),
      Pair(
        AccountProviderDescriptionCollectionSerializersType::class.java,
        this.parameters.accountProviderSerializers
      )
    )

  companion object {

    private val LOG =
      LoggerFactory.getLogger(DBInstance::class.java)

    const val DATABASE_SQLITE_ID: Int = 0x50504442
    const val DATABASE_APPLICATION_ID: String = "org.thepalaceproject.palace"

    fun open(
      parameters: DBParameters
    ): DBType {
      try {
        val absFile = parameters.file.toAbsolutePath()
        this.createOrUpgrade(absFile)
        return this.doOpen(parameters, absFile)
      } catch (e: Exception) {
        throw IOException(e)
      }
    }

    private fun createOrUpgrade(
      file: Path?
    ) {
      val url = java.lang.StringBuilder(128)
      url.append("jdbc:sqlite:")
      url.append(file)

      val config = SQLiteConfig()
      config.setApplicationId(this.DATABASE_SQLITE_ID)
      config.enforceForeignKeys(true)
      config.setOpenMode(SQLiteOpenMode.CREATE)
      config.setLockingMode(SQLiteConfig.LockingMode.NORMAL)
      config.setJournalMode(SQLiteConfig.JournalMode.WAL)

      val dataSource = SQLiteDataSource(config)
      dataSource.setUrl(url.toString())

      val parsers = TrSchemaRevisionSetParsers()
      val revisions: TrSchemaRevisionSet
      DBInstance::class.java.getResourceAsStream(
        "/org/thepalaceproject/db/internal/database.xml"
      ).use { stream ->
        parsers.createParserWithContext(
          JXEHardenedSAXParsers(Supplier { SAXParserFactoryImpl() }),
          URI.create("urn:source"),
          stream,
          Consumer { status: ParseStatus? -> this.LOG.trace("Parser: {}", status) }
        ).use { parser ->
          revisions = parser.execute()
        }
      }
      val arguments =
        TrArguments(Map.of<String?, TrArgumentType?>())

      dataSource.connection.use { connection ->
        this.setWALMode(connection)
        connection.setAutoCommit(false)

        TrExecutors().create(
          TrExecutorConfiguration(
            { connection: Connection -> this.schemaVersionGet(connection) },
            { version: BigInteger, connection: Connection ->
              this.schemaVersionSet(version, connection)
            },
            { event: TrEventType? -> this.logEvent(event) },
            revisions,
            TrExecutorUpgrade.PERFORM_UPGRADES,
            arguments,
            connection
          )
        ).execute()
        connection.commit()
      }
    }

    @Throws(SQLException::class)
    private fun schemaVersionSet(
      version: BigInteger,
      connection: Connection
    ) {
      val statementText: String
      if (version == BigInteger.ZERO) {
        statementText =
          "insert into schema_version (version_application_id, version_number) values (?, ?)"
        connection.prepareStatement(statementText).use { statement ->
          statement.setString(1, this.DATABASE_APPLICATION_ID)
          statement.setLong(2, version.toLong())
          statement.execute()
        }
      } else {
        statementText = "update schema_version set version_number = ?"
        connection.prepareStatement(statementText).use { statement ->
          statement.setLong(1, version.toLong())
          statement.execute()
        }
      }
    }

    private fun schemaVersionGet(
      connection: Connection
    ): Optional<BigInteger> {
      try {
        val statementText =
          "SELECT version_application_id, version_number FROM schema_version"
        this.LOG.debug("execute: {}", statementText)

        connection.prepareStatement(statementText).use { statement ->
          statement.executeQuery().use { result ->
            if (!result.next()) {
              throw SQLException("schema_version table is empty!")
            }
            val applicationCA =
              result.getString(1)
            val version =
              result.getLong(2)

            if (applicationCA != this.DATABASE_APPLICATION_ID) {
              throw SQLException(
                String.format(
                  "Database application ID is %s but should be %s",
                  applicationCA,
                  this.DATABASE_APPLICATION_ID
                )
              )
            }
            return Optional.of(BigInteger.valueOf(version))
          }
        }
      } catch (e: SQLException) {
        if (e.errorCode == SQLiteErrorCode.SQLITE_ERROR.code) {
          connection.rollback()
          return Optional.empty<BigInteger>()
        }
        throw e
      }
    }

    private fun setWALMode(
      connection: Connection
    ) {
      connection.createStatement().use { st ->
        st.execute("PRAGMA journal_mode=WAL;")
      }
    }

    private fun logEvent(
      event: TrEventType?
    ) {
      if (event is TrEventExecutingSQL) {
        this.LOG.trace("Executing SQL: {}", event.statement())
      } else if (event is TrEventUpgrading) {
        this.LOG.info(
          "Upgrading schema: {} -> {}",
          event.fromVersion(),
          event.toVersion()
        )
      }
    }

    private fun doOpen(parameters: DBParameters, file: Path): DBType {
      val url = StringBuilder(128)
      url.append("jdbc:sqlite:")
      url.append(file)

      val config = SQLiteConfig()
      config.setApplicationId(this.DATABASE_SQLITE_ID)
      config.enforceForeignKeys(true)
      config.setLockingMode(SQLiteConfig.LockingMode.NORMAL)
      config.setJournalMode(SQLiteConfig.JournalMode.WAL)

      val dataSource = SQLiteDataSource(config)
      dataSource.url = url.toString()
      return DBInstance(parameters = parameters, dataSource = dataSource)
    }
  }

  private inner class DBConnection(
    override val connection: Connection
  ) : DBConnectionType {
    private val closed = AtomicBoolean()

    private fun checkNotClosed() {
      check(!this.closed.get()) {
        "Connection must not be closed."
      }
    }

    override fun openTransaction(
      closeBehavior: DBTransactionCloseBehavior
    ): DBTransactionType {
      this.checkNotClosed()

      return DBTransaction(
        connection = this,
        onClose = when (closeBehavior) {
          DBTransactionCloseBehavior.ON_CLOSE_CLOSE_CONNECTION -> {
            Runnable {
              this.close()
            }
          }

          DBTransactionCloseBehavior.ON_CLOSE_DO_NOTHING -> {
            Runnable {
            }
          }
        }
      )
    }

    override fun close() {
      if (this.closed.compareAndSet(false, true)) {
        this.connection.rollback()
        this.connection.close()
      }
    }

    fun <T> service(clazz: Class<T>): T {
      return this@DBInstance.service(clazz)
    }
  }

  private fun <T> service(
    clazz: Class<T>
  ): T {
    val service =
      this.services[clazz]
        ?: throw IllegalArgumentException("No such service registered '$clazz'")
    return service as T
  }

  private class DBTransaction(
    override val connection: DBConnection,
    private val onClose: Runnable
  ) : DBTransactionType {

    private val closed = AtomicBoolean()

    override fun <T> service(
      clazz: Class<T>
    ): T {
      return this.connection.service(clazz)
    }

    override fun <P, R, Q : DBQueryType<P, R>> query(
      queryType: Class<Q>
    ): Q {
      this.checkNotClosed()
      return DBQueries.query(queryType)
    }

    private fun checkNotClosed() {
      check(!this.closed.get()) {
        "Transaction must not be closed."
      }
    }

    override fun rollback() {
      this.checkNotClosed()
      this.connection.connection.rollback()
    }

    override fun commit() {
      this.checkNotClosed()
      this.connection.connection.commit()
    }

    override fun close() {
      this.rollback()
      if (this.closed.compareAndSet(false, true)) {
        this.onClose.run()
      }
    }
  }

  override fun openConnection(): DBConnectionType {
    val connection = this.dataSource.connection
    setWALMode(connection)
    connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
    connection.autoCommit = false
    return DBConnection(connection)
  }

  override fun close() {
    if (this.closed.compareAndSet(false, true)) {
      // Nothing to close, currently
    }
  }
}
