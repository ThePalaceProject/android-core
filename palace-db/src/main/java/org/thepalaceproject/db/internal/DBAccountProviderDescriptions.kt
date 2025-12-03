package org.thepalaceproject.db.internal

import org.joda.time.DateTimeZone
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollection
import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollectionParsersType
import org.nypl.simplified.parser.api.ParseError
import org.nypl.simplified.parser.api.ParseResult
import org.slf4j.LoggerFactory
import org.thepalaceproject.db.api.DBException
import org.thepalaceproject.db.api.DBTransactionType
import org.thepalaceproject.db.internal.DBQAccountProviderDescriptionGet.FORMAT_OPDS2_COLLECTION
import java.net.URI
import java.sql.ResultSet

internal object DBAccountProviderDescriptions {

  private val logger =
    LoggerFactory.getLogger(DBAccountProviderDescriptions::class.java)

  fun forceUTC(description: AccountProviderDescription): AccountProviderDescription {
    return description.copy(
      updated = description.updated.withZone(DateTimeZone.UTC)
    )
  }

  fun parseFromResult(
    transaction: DBTransactionType,
    resultSet: ResultSet
  ): AccountProviderDescription {
    return when (val result = resultSet.getString("apd_data_format")) {
      FORMAT_OPDS2_COLLECTION -> {
        this.parseFromOPDS2Collection(transaction, resultSet)
      }

      else -> {
        throw DBException("Unsupported format: $result", Exception())
      }
    }
  }

  private fun parseFromOPDS2Collection(
    transaction: DBTransactionType,
    resultSet: ResultSet
  ): AccountProviderDescription {
    val parsers =
      transaction.service(AccountProviderDescriptionCollectionParsersType::class.java)

    return resultSet.getBinaryStream("apd_data").use { data ->
      val parser =
        parsers.createParser(URI.create("urn:database"), data, false)

      when (val result = parser.parse()) {
        is ParseResult.Failure<AccountProviderDescriptionCollection> -> {
          throw DBException(
            message = result.errors.joinToString("\n", transform = this::showParseError),
            cause = Exception()
          )
        }

        is ParseResult.Success<AccountProviderDescriptionCollection> -> {
          this.forceUTC(result.result.providers[0])
        }
      }
    }
  }

  private fun showParseError(
    error: ParseError
  ): String {
    this.logger.error(
      "{}:{}:{}: {}: ",
      error.source,
      error.line,
      error.column,
      error.message,
      error.exception
    )

    return buildString {
      this.append(error.line)
      this.append(':')
      this.append(error.column)
      this.append(": ")
      this.append(error.message)
      val ex = error.exception
      if (ex != null) {
        this.append(ex.message)
        this.append(" (")
        this.append(ex.javaClass.simpleName)
        this.append(")")
      }
    }
  }
}
