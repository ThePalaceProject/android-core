package org.nypl.simplified.books.time.tracking

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

object TimeTrackingInfoFileUtils {

  fun saveTimeTrackingInfoOnFile(timeTrackingInfo: TimeTrackingInfo, file: File) {
    val json = TimeTrackingJSON.serializeTimeTrackingToJSON(
      mapper = ObjectMapper(),
      timeTrackingInfo = timeTrackingInfo
    )

    file.writeBytes(json.toString().toByteArray())
  }

  fun getTimeTrackingInfoFromFile(file: File): TimeTrackingInfo {
    return TimeTrackingJSON.deserializeBytesToTimeTrackingInfo(
      bytes = file.readBytes()
    )
  }
}
