package org.thepalaceproject.db.api

interface DBQueryType<P, R> {
  fun execute(
    transaction: DBTransactionType,
    parameters: P
  ): R
}
