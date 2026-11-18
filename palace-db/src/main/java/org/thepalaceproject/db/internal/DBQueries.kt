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
import org.thepalaceproject.db.api.queries.DBQAccountRegistrySettingsGetType
import org.thepalaceproject.db.api.queries.DBQAccountRegistrySettingsPutType
import org.thepalaceproject.db.api.queries.DBQSchemaVersionType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryDeleteType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntryPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEntrySelectType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPurgeType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingEventSelectType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanDeleteType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanPutType
import org.thepalaceproject.db.api.queries.DBQTimeTrackingSpanSelectType

internal object DBQueries {
  private val queryList =
    mapOf<Class<*>, DBQueryType<*, *>>(
      Pair(
        DBQAccountProviderDescriptionDeleteAllType::class.java,
        DBQAccountProviderDescriptionDeleteAll,
      ),
      Pair(
        DBQAccountProviderDescriptionDeleteType::class.java,
        DBQAccountProviderDescriptionDelete,
      ),
      Pair(DBQAccountProviderDescriptionGetType::class.java, DBQAccountProviderDescriptionGet),
      Pair(DBQAccountProviderDescriptionIDSetType::class.java, DBQAccountProviderDescriptionIDSet),
      Pair(DBQAccountProviderDescriptionListType::class.java, DBQAccountProviderDescriptionList),
      Pair(DBQAccountProviderDescriptionPutType::class.java, DBQAccountProviderDescriptionPut),
      Pair(DBQAccountProviderGetType::class.java, DBQAccountProviderGet),
      Pair(DBQAccountProviderListType::class.java, DBQAccountProviderList),
      Pair(DBQAccountProviderPutType::class.java, DBQAccountProviderPut),
      Pair(DBQSchemaVersionType::class.java, DBQSchemaVersion),
      Pair(DBQAccountRegistrySettingsPutType::class.java, DBQAccountRegistrySettingsPut),
      Pair(DBQAccountRegistrySettingsGetType::class.java, DBQAccountRegistrySettingsGet),
      Pair(DBQTimeTrackingEntryDeleteType::class.java, DBQTimeTrackingEntryDelete),
      Pair(DBQTimeTrackingEntryPutType::class.java, DBQTimeTrackingEntryPut),
      Pair(DBQTimeTrackingEntrySelectType::class.java, DBQTimeTrackingEntrySelect),
      Pair(DBQTimeTrackingEventPurgeType::class.java, DBQTimeTrackingEventPurge),
      Pair(DBQTimeTrackingEventPutType::class.java, DBQTimeTrackingEventPut),
      Pair(DBQTimeTrackingEventSelectType::class.java, DBQTimeTrackingEventSelect),
      Pair(DBQTimeTrackingSpanDeleteType::class.java, DBQTimeTrackingSpanDelete),
      Pair(DBQTimeTrackingSpanPutType::class.java, DBQTimeTrackingSpanPut),
      Pair(DBQTimeTrackingSpanSelectType::class.java, DBQTimeTrackingSpanSelect),
    )

  fun <P, R, Q : DBQueryType<P, R>> query(clazz: Class<Q>): Q {
    val query =
      this.queryList[clazz]
        ?: throw IllegalArgumentException("No query registered for '$clazz'")
    return query as Q
  }
}
