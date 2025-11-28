package org.thepalaceproject.db.api

interface DBFactoryType {
  fun open(parameters: DBParameters): DBType
}
