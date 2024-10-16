package org.nypl.simplified.books.time.tracking

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.nypl.simplified.json.core.JSONParseException
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

  private val objectMapper = ObjectMapper()

  fun serializeRequest(
    request: TimeTrackingRequest
  ): ObjectNode {
    val node = this.objectMapper.createObjectNode()
    node.put(this.NODE_BOOK_ID, request.bookId)
    node.put(this.NODE_LIBRARY_ID, request.libraryId.toString())

    val timeEntriesArray = this.objectMapper.createArrayNode()
    request.timeEntries.forEach { entry ->
      val entryNode = this.objectMapper.createObjectNode()
      entryNode.put(this.NODE_ID, entry.id)
      entryNode.put(this.NODE_DURING_MINUTE, entry.duringMinute)
      entryNode.put(this.NODE_SECONDS_PLAYED, entry.secondsPlayed)
      timeEntriesArray.add(entryNode)
    }
    return node
  }

  fun serializeToBytes(
    request: TimeTrackingRequest
  ): ByteArray {
    return this.objectMapper.writeValueAsBytes(serializeRequest(request))
  }

  @Throws(JSONParseException::class)
  fun deserializeResponse(
    data: ByteArray
  ): TimeTrackingServerResponse {
    val r = this.objectMapper.readTree(data)
    return when (r) {
      is ObjectNode -> {
        deserializeResponseObject(r)
      }

      else -> {
        throw JSONParseException(
          "Server returned an unparseable JSON response (expected Object, but got ${r.javaClass})"
        )
      }
    }
  }

  @Throws(JSONParseException::class)
  fun deserializeResponseObject(
    o: ObjectNode
  ): TimeTrackingServerResponse {
    val responsesNode =
      JSONParserUtilities.getArray(o, this.NODE_RESPONSES)

    val responses = arrayListOf<TimeTrackingServerResponseEntry>()
    responsesNode.forEach { responseNode ->
      responses.add(
        TimeTrackingServerResponseEntry(
          id = responseNode.get(this.NODE_ID).asText(),
          message = responseNode.get(this.NODE_MESSAGE).asText(),
          status = responseNode.get(this.NODE_STATUS).asInt()
        )
      )
    }

    val summaryNode =
      JSONParserUtilities.getObject(o, this.NODE_SUMMARY)

    val summary =
      TimeTrackingServerResponseSummary(
        failures = JSONParserUtilities.getInteger(summaryNode, this.NODE_FAILURES),
        successes = JSONParserUtilities.getInteger(summaryNode, this.NODE_SUCCESSES),
        total = JSONParserUtilities.getInteger(summaryNode, this.NODE_TOTAL)
      )

    return TimeTrackingServerResponse(
      summary = summary,
      responses = responses.toList()
    )
  }

  private fun convertTimeTrackingToJSON(
    objectMapper: ObjectMapper,
    node: ObjectNode,
    timeTrackingInfo: TimeTrackingInfo
  ): ObjectNode {
    node.put(this.NODE_BOOK_ID, timeTrackingInfo.bookId)
    node.put(this.NODE_LIBRARY_ID, timeTrackingInfo.libraryId)

    val timeEntriesArray = objectMapper.createArrayNode()

    timeTrackingInfo.timeEntries.forEach { entry ->
      timeEntriesArray.add(
        objectMapper.createObjectNode().apply {
          this.put(this@TimeTrackingJSON.NODE_ID, entry.id)
          this.put(this@TimeTrackingJSON.NODE_DURING_MINUTE, entry.duringMinute)
          this.put(this@TimeTrackingJSON.NODE_SECONDS_PLAYED, entry.secondsPlayed)
        }
      )
    }

    node.set<ArrayNode>(this.NODE_TIME_ENTRIES, timeEntriesArray)

    return node
  }

  fun convertServerResponseToTimeTrackingResponse(
    objectNode: ObjectNode,
  ): TimeTrackingServerResponse? {
    return try {
      val responsesNode = JSONParserUtilities.getArray(objectNode, this.NODE_RESPONSES)
      val responses = arrayListOf<TimeTrackingServerResponseEntry>()
      responsesNode.forEach { responseNode ->
        responses.add(
          TimeTrackingServerResponseEntry(
            id = responseNode.get(this.NODE_ID).asText(),
            message = responseNode.get(this.NODE_MESSAGE).asText(),
            status = responseNode.get(this.NODE_STATUS).asInt()
          )
        )
      }

      val summaryNode = objectNode.get(this.NODE_SUMMARY)
      val summary = TimeTrackingServerResponseSummary(
        failures = summaryNode.get(this.NODE_FAILURES).asInt(),
        successes = summaryNode.get(this.NODE_SUCCESSES).asInt(),
        total = summaryNode.get(this.NODE_TOTAL).asInt()
      )

      TimeTrackingServerResponse(
        summary = summary,
        responses = responses
      )
    } catch (e: Exception) {
      this.logger.error("Error converting server response to time tracking response: ", e)
      null
    }
  }

  fun convertTimeTrackingToLocalJSON(
    objectMapper: ObjectMapper,
    timeTrackingInfo: TimeTrackingInfo
  ): ObjectNode {
    val node = objectMapper.createObjectNode()
    node.put(this.NODE_ACCOUNT_ID, timeTrackingInfo.accountId)
    node.put(this.NODE_URI, timeTrackingInfo.timeTrackingUri.toString())
    return this.convertTimeTrackingToJSON(
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
      this.convertTimeTrackingToJSON(
        objectMapper = objectMapper,
        node = objectMapper.createObjectNode(),
        timeTrackingInfo = timeTrackingInfo
      )
    )
  }

  fun convertBytesToTimeTrackingInfo(
    bytes: ByteArray
  ): TimeTrackingInfo? {
    return try {
      val mapper = ObjectMapper()
      val node = mapper.readTree(bytes)

      if (node is MissingNode) {
        return null
      }

      val timeEntriesJSON =
        JSONParserUtilities.getArray(node as ObjectNode, this.NODE_TIME_ENTRIES)

      val timeEntries = timeEntriesJSON.map { entry ->
        TimeTrackingEntry(
          id = entry.get(this.NODE_ID).asText(),
          duringMinute = entry.get(this.NODE_DURING_MINUTE).asText(),
          secondsPlayed = entry.get(this.NODE_SECONDS_PLAYED).asInt()
        )
      }

      TimeTrackingInfo(
        accountId = node.get(this.NODE_ACCOUNT_ID).asText(),
        bookId = node.get(this.NODE_BOOK_ID).asText(),
        libraryId = node.get(this.NODE_LIBRARY_ID).asText(),
        timeEntries = timeEntries,
        timeTrackingUri = URI(node.get(this.NODE_URI).asText())
      )
    } catch (e: Exception) {
      this.logger.error("Error converting bytes from file: ", e)
      null
    }
  }
}
