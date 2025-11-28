package org.thepalaceproject.db.api

import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollectionParsersType
import org.nypl.simplified.accounts.api.AccountProviderDescriptionCollectionSerializersType
import java.nio.file.Path

data class DBParameters(
  val file: Path,
  val accountProviderParsers: AccountProviderDescriptionCollectionParsersType,
  val accountProviderSerializers: AccountProviderDescriptionCollectionSerializersType
)
