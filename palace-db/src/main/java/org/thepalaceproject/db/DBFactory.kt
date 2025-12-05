package org.thepalaceproject.db

import org.thepalaceproject.db.api.DBFactoryType
import org.thepalaceproject.db.api.DBParameters
import org.thepalaceproject.db.api.DBType
import org.thepalaceproject.db.internal.DBInstance

object DBFactory : DBFactoryType {
  override fun open(parameters: DBParameters): DBType {
    return DBInstance.open(parameters)
  }
}
