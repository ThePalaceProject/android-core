package org.thepalaceproject.db.internal

import org.thepalaceproject.db.api.DBQueryType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionDeleteAllType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionDeleteType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionGetType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionIDSetType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionListType
import org.thepalaceproject.db.api.queries.DBQAccountProviderDescriptionPutType
import org.thepalaceproject.db.api.queries.DBQAccountProviderGetType
import org.thepalaceproject.db.api.queries.DBQAccountProviderListType
import org.thepalaceproject.db.api.queries.DBQAccountProviderPutType
import org.thepalaceproject.db.api.queries.DBQSchemaVersionType

internal object DBQueries {

  private val queryList =
    mapOf<Class<*>, DBQueryType<*, *>>(
      Pair(
        DBQAccountProviderDescriptionDeleteAllType::class.java,
        DBQAccountProviderDescriptionDeleteAll
      ),
      Pair(
        DBQAccountProviderDescriptionDeleteType::class.java,
        DBQAccountProviderDescriptionDelete
      ),
      Pair(DBQAccountProviderDescriptionGetType::class.java, DBQAccountProviderDescriptionGet),
      Pair(DBQAccountProviderDescriptionIDSetType::class.java, DBQAccountProviderDescriptionIDSet),
      Pair(DBQAccountProviderDescriptionListType::class.java, DBQAccountProviderDescriptionList),
      Pair(DBQAccountProviderDescriptionPutType::class.java, DBQAccountProviderDescriptionPut),
      Pair(DBQAccountProviderGetType::class.java, DBQAccountProviderGet),
      Pair(DBQAccountProviderListType::class.java, DBQAccountProviderList),
      Pair(DBQAccountProviderPutType::class.java, DBQAccountProviderPut),
      Pair(DBQSchemaVersionType::class.java, DBQSchemaVersion),
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
