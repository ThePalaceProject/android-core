package org.thepalaceproject.db.api.queries

import java.time.OffsetDateTime

sealed interface DBQAccountRegistrySetting {

  val name: String

  data class TimeSetting(
    override val name: String,
    val value: OffsetDateTime
  ) : DBQAccountRegistrySetting
}
