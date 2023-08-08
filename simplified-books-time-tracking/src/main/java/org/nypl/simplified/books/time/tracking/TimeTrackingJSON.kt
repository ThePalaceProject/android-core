package org.nypl.simplified.books.time.tracking

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.nypl.simplified.json.core.JSONParserUtilities
import org.slf4j.LoggerFactory
import java.net.URI

object TimeTrackingJSON {

  private val logger = LoggerFactory.getLogger(TimeTrackingJSON::class.java)

  private const val NODE_ACCOUNT_ID = "accountId"
  private const val NODE_BOOK_ID = "bookId"
  private const val NODE_DURING_MINUTE = "duringMinute"
  private const val NODE_FAILURES = "failures"
  private const val NODE_ID = "id"
  private const val NODE_LIBRARY_ID = "libraryId"
  private const val NODE_MESSAGE = "message"
  private const val NODE_RESPONSES = "responses"
  private const val NODE_SECONDS_PLAYED = "secondsPlayed"
  private const val NODE_STATUS = "status"
  private const val NODE_SUCCESSES = "successes"
  private const val NODE_SUMMARY = "summary"
  private const val NODE_TIME_ENTRIES = "timeEntries"
  private const val NODE_TOTAL = "total"
  private const val NODE_URI = "uri"

  private fun convertTimeTrackingToJSON(
    objectMapper: ObjectMapper,
    node: ObjectNode,
    timeTrackingInfo: TimeTrackingInfo
  ): ObjectNode {
    node.put(NODE_BOOK_ID, timeTrackingInfo.bookId)
    node.put(NODE_LIBRARY_ID, timeTrackingInfo.libraryId)

    val timeEntriesArray = objectMapper.createArrayNode()

    timeTrackingInfo.timeEntries.forEach { entry ->
      timeEntriesArray.add(
        objectMapper.createObjectNode().apply {
          put(NODE_ID, entry.id)
          put(NODE_DURING_MINUTE, entry.duringMinute)
          put(NODE_SECONDS_PLAYED, entry.secondsPlayed)
        }
      )
    }

    node.set<ArrayNode>(NODE_TIME_ENTRIES, timeEntriesArray)

    return node
  }

  fun convertServerResponseToTimeTrackingResponse(
    objectNode: ObjectNode,
  ): TimeTrackingResponse? {
    return try {
      val responsesNode = JSONParserUtilities.getArray(objectNode, NODE_RESPONSES)
      val responses = arrayListOf<TimeTrackingResponseEntry>()
      responsesNode.forEach { responseNode ->
        responses.add(
          TimeTrackingResponseEntry(
            id = responseNode.get(NODE_ID).asText(),
            message = responseNode.get(NODE_MESSAGE).asText(),
            status = responseNode.get(NODE_STATUS).asInt()
          )
        )
      }

      val summaryNode = objectNode.get(NODE_SUMMARY)
      val summary = TimeTrackingResponseSummary(
        failures = summaryNode.get(NODE_FAILURES).asInt(),
        successes = summaryNode.get(NODE_SUCCESSES).asInt(),
        total = summaryNode.get(NODE_TOTAL).asInt()
      )

      TimeTrackingResponse(
        summary = summary,
        responses = responses
      )
    } catch (e: Exception) {
      logger.error("Error converting server response to time tracking response: ", e)
      null
    }
  }

  fun convertTimeTrackingToLocalJSON(
    objectMapper: ObjectMapper,
    timeTrackingInfo: TimeTrackingInfo
  ): ObjectNode {
    val node = objectMapper.createObjectNode()
    node.put(NODE_ACCOUNT_ID, timeTrackingInfo.accountId)
    node.put(NODE_URI, timeTrackingInfo.timeTrackingUri.toString())
    return convertTimeTrackingToJSON(
      objectMapper = objectMapper,
      node = node,
      timeTrackingInfo = timeTrackingInfo
    )
  }

  fun convertTimeTrackingInfoToBytes(
    objectMapper: ObjectMapper,
    timeTrackingInfo: TimeTrackingInfo
  ): ByteArray {
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, false)
    objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
    return objectMapper.writeValueAsBytes(
      convertTimeTrackingToJSON(
        objectMapper = objectMapper,
        node = objectMapper.createObjectNode(),
        timeTrackingInfo = timeTrackingInfo
      )
    )
  }

  fun convertBytesToTimeTrackingInfo(bytes: ByteArray): TimeTrackingInfo? {
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
        accountId = node.get(NODE_ACCOUNT_ID).asText(),
        bookId = node.get(NODE_BOOK_ID).asText(),
        libraryId = node.get(NODE_LIBRARY_ID).asText(),
        timeEntries = timeEntries,
        timeTrackingUri = URI(node.get(NODE_URI).asText())
      )
    } catch (e: Exception) {
      logger.error("Error converting bytes from file: ", e)
      e.printStackTrace()
      null
    }
  }
}
