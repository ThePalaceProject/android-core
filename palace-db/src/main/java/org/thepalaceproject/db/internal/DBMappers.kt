package org.thepalaceproject.db.internal

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

internal object DBMappers {

  val mapper =
    JsonMapper.builder()
      .addModule(JavaTimeModule())
      .addModule(Jdk8Module())
      .build()
}
