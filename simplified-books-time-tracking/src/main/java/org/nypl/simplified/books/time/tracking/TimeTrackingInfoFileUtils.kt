package org.nypl.simplified.books.time.tracking

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

object TimeTrackingInfoFileUtils {

  fun saveTimeTrackingInfoOnFile(timeTrackingInfo: TimeTrackingInfo, file: File) {
    val json = TimeTrackingJSON.convertTimeTrackingToLocalJSON(
      mapper = ObjectMapper(),
      timeTrackingInfo = timeTrackingInfo
    )

    file.writeBytes(json.toString().toByteArray())
  }

  fun getTimeTrackingInfoFromFile(file: File): TimeTrackingInfo? {
    return TimeTrackingJSON.convertBytesToTimeTrackingInfo(
      bytes = file.readBytes()
    )
  }

  fun addEntriesToFile(entries: List<TimeTrackingEntry>, file: File) {
    val currentTimeTrackingInfo = getTimeTrackingInfoFromFile(file) ?: return
    saveTimeTrackingInfoOnFile(
      timeTrackingInfo = currentTimeTrackingInfo.copy(
        timeEntries = ArrayList(currentTimeTrackingInfo.timeEntries).apply {
          addAll(entries)
        }
      ),
      file = file
    )
  }
}
