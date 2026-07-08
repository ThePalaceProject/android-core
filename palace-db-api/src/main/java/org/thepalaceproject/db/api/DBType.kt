package org.thepalaceproject.db.api

import java.nio.file.Path

/**
 * A database.
 */

interface DBType : AutoCloseable {
  /**
   * Open a new database connection.
   */

  fun openConnection(): DBConnectionType

  /**
   * Open a new one-shot database transaction.
   */

  fun openTransaction(): DBTransactionType = this.openConnection().openTransaction(DBTransactionCloseBehavior.ON_CLOSE_CLOSE_CONNECTION)

  /**
   * Attempt to copy any account provider descriptions from the given database file.
   */

  fun copyAccountProviderDescriptionsFrom(file: Path)
}
