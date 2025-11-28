package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBQueryType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionGetType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionListType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionPutType
import org.thepalaceproject.db.api.queries.DBQSchemaVersionType

object DBQueries {

  private val queryList =
    mapOf<Class<*>, DBQueryType<*, *>>(
      Pair(DBQSchemaVersionType::class.java, DBQSchemaVersion),
      Pair(DBQAccountProviderDescriptionGetType::class.java, DBQAccountProviderDescriptionGet),
      Pair(DBQAccountProviderDescriptionPutType::class.java, DBQAccountProviderDescriptionPut),
      Pair(DBQAccountProviderDescriptionListType::class.java, DBQAccountProviderDescriptionList)
    )

  fun <P, R, Q : DBQueryType<P, R>> query(
    clazz: Class<Q>
  ): Q {
    val query =
      this.queryList[clazz]
        ?: throw IllegalArgumentException("No query registered for '$clazz'")
    return query as Q
  }
}
