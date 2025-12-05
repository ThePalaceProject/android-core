package org.thepalaceproject.db.api

/**
 * The close behaviour for a transaction.
 */

enum class DBTransactionCloseBehavior {
  /**
   * Close the connection that owns the transaction when the transaction
   * is closed.
   */

  ON_CLOSE_CLOSE_CONNECTION,

  /**
   * Do nothing with the connection that owns the transaction when the
   * transaction is closed.
   */

  ON_CLOSE_DO_NOTHING
}
