package org.nypl.simplified.books.time.tracking

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.nypl.simplified.json.core.JSONParseException
import org.nypl.simplified.json.core.JSONParserUtilities

object TimeTrackingJSON {

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

  private val objectMapper = ObjectMapper()

  fun serializeRequest(
    request: TimeTrackingRequest
  ): ObjectNode {
    val node = this.objectMapper.createObjectNode()
    node.put(this.NODE_BOOK_ID, request.bookId)
    node.put(this.NODE_LIBRARY_ID, request.libraryId.toString())

    val timeEntriesArray = node.putArray(this.NODE_TIME_ENTRIES)
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
}
