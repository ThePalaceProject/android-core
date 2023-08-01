package org.nypl.simplified.books.time.tracking

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.nypl.simplified.json.core.JSONParserUtilities
import java.net.URI

object TimeTrackingJSON {

  private const val NODE_BOOK_ID = "book_id"
  private const val NODE_DURING_MINUTE = "duringMinute"
  private const val NODE_ID = "id"
  private const val NODE_LIBRARY_ID = "library_id"
  private const val NODE_SECONDS_PLAYED = "secondsPlayed"
  private const val NODE_TIME_ENTRIES = "timeEntries"
  private const val NODE_URI = "uri"

  fun serializeTimeTrackingToJSON(
    mapper: ObjectMapper,
    timeTrackingInfo: TimeTrackingInfo
  ): ObjectNode {
    val node = mapper.createObjectNode()

    node.put(NODE_BOOK_ID, timeTrackingInfo.bookId)
    node.put(NODE_LIBRARY_ID, timeTrackingInfo.libraryId)
    node.put(NODE_URI, timeTrackingInfo.timeTrackingUri.toString())

    val timeEntriesArray = mapper.createArrayNode()

    timeTrackingInfo.timeEntries.forEach { entry ->
      timeEntriesArray.add(
        mapper.createObjectNode().apply {
          put(NODE_ID, entry.id)
          put(NODE_DURING_MINUTE, entry.duringMinute)
          put(NODE_SECONDS_PLAYED, entry.secondsPlayed)
        }
      )
    }

    node.set<ArrayNode>(NODE_TIME_ENTRIES, timeEntriesArray)

    return node
  }

  fun serializeTimeTrackingInfoToBytes(
    objectMapper: ObjectMapper,
    timeTrackingInfo: TimeTrackingInfo
  ): ByteArray {
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, false)
    objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
    return objectMapper.writeValueAsBytes(
      serializeTimeTrackingToJSON(
        objectMapper,
        timeTrackingInfo
      )
    )
  }

  fun deserializeBytesToTimeTrackingInfo(bytes: ByteArray): TimeTrackingInfo {
    return try {
      val mapper = ObjectMapper()
      val node = mapper.readTree(bytes)

      val timeEntriesJSON = JSONParserUtilities.getArray(node as ObjectNode, NODE_TIME_ENTRIES)

      val timeEntries = timeEntriesJSON.map { entry ->
        TimeTrackingEntry(
          id = entry.get(NODE_ID).asText(),
          duringMinute = entry.get(NODE_DURING_MINUTE).asText(),
          secondsPlayed = entry.get(NODE_SECONDS_PLAYED).asInt()
        )
      }

      TimeTrackingInfo(
        libraryId = node.get(NODE_LIBRARY_ID).asText(),
        bookId = node.get(NODE_BOOK_ID).asText(),
        timeEntries = timeEntries,
        timeTrackingUri = try {
          URI(node.get(NODE_URI).asText())
        } catch (exception: Exception) {
          exception.printStackTrace()
          null
        }
      )
    } catch (e: Exception) {
      Log.d("TimeTrackingJSON", "Error converting bytes to TimeTrackingInfo")
      TimeTrackingInfo(
        libraryId = "",
        bookId = "",
        timeEntries = listOf(),
        timeTrackingUri = null
      )
    }
  }
}
