package org.thepalaceproject.db.api

import java.sql.Connection

/**
 * A database connection.
 */

interface DBConnectionType : AutoCloseable {

  /**
   * The underlying SQL connection
   */
  val connection: Connection

  /**
   * Begin a new transaction. The transaction will be registered as a
   * closeable resource with this connection.
   *
   * @return The transaction
   */

  fun openTransaction(): DBTransactionType {
    return this.openTransaction(DBTransactionCloseBehavior.ON_CLOSE_DO_NOTHING)
  }

  /**
   * Begin a new transaction. The transaction will be registered as a
   * closeable resource with this connection.
   *
   * @param closeBehavior The close behavior
   *
   * @return The transaction
   */

  fun openTransaction(
    closeBehavior: DBTransactionCloseBehavior
  ): DBTransactionType
}
