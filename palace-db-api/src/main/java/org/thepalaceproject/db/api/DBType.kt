package org.thepalaceproject.db.api

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

  fun openTransaction(): DBTransactionType {
    return this.openConnection().openTransaction(DBTransactionCloseBehavior.ON_CLOSE_CLOSE_CONNECTION)
  }
}
