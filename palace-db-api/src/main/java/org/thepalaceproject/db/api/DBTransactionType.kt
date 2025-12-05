package org.thepalaceproject.db.api

/**
 * A database transaction.
 */

interface DBTransactionType : AutoCloseable {

  /**
   * The underlying connection
   */

  val connection: DBConnectionType

  /**
   * Obtain a registered service on the transaction.
   */

  fun <T> service(clazz: Class<T>): T

  /**
   * Retrieve a query.
   */

  fun <P, R, Q : DBQueryType<P, R>> query(
    queryType: Class<Q>
  ): Q

  /**
   * Find a query and execute it.
   */

  fun <P, R, Q : DBQueryType<P, R>> execute(
    queryType: Class<Q>,
    parameters: P
  ): R {
    return this.query(queryType).execute(this, parameters)
  }

  /**
   * Roll back the transaction.
   */

  fun rollback()

  /**
   * Commit the transaction.
   */

  fun commit()
}
