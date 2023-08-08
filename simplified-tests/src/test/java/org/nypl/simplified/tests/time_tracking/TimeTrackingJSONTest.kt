package org.nypl.simplified.tests.time_tracking

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.nypl.simplified.books.time.tracking.TimeTrackingEntry
import org.nypl.simplified.books.time.tracking.TimeTrackingInfo
import org.nypl.simplified.books.time.tracking.TimeTrackingJSON
import org.nypl.simplified.json.core.JSONParserUtilities
import java.net.URI

class TimeTrackingJSONTest {

  private lateinit var objectMapper: ObjectMapper
  private lateinit var formatter: DateTimeFormatter

  @BeforeEach
  fun testSetup() {
    this.objectMapper = ObjectMapper()
    this.formatter = ISODateTimeFormat.dateTime().withZoneUTC()
  }

  @AfterEach
  fun tearDown() {
    DateTimeZone.setDefault(DateTimeZone.getDefault())
  }

  @Test
  fun testTimeTrackingInfoFromBytes() {
    val timeTrackingInfoBytes = "{" +
      "\"accountId\":\"accountId\"," +
      "\"bookId\":\"bookId\"," +
      "\"libraryId\":\"libraryId\"," +
      "\"uri\":\"https://projectpalace.io/timeTracking\"," +
      "\"timeEntries\": [" +
      "{\"id\":\"id\",\"duringMinute\":\"2023-08-08T12:50Z\",\"secondsPlayed\":50}" +
      "]" +
      "}"

    val timeTrackingInfo = TimeTrackingJSON.convertBytesToTimeTrackingInfo(
      bytes = timeTrackingInfoBytes.toByteArray()
    )

    assertNotNull(timeTrackingInfo)
    assertEquals(timeTrackingInfo?.accountId, "accountId")
    assertEquals(timeTrackingInfo?.bookId, "bookId")
    assertEquals(timeTrackingInfo?.libraryId, "libraryId")
    assertEquals(timeTrackingInfo?.timeTrackingUri, URI.create("https://projectpalace.io/timeTracking"))
    assertEquals(timeTrackingInfo?.timeEntries.orEmpty().size, 1)

    val entry = timeTrackingInfo?.timeEntries.orEmpty().first()
    assertEquals(entry.id, "id")
    assertEquals(entry.duringMinute, "2023-08-08T12:50Z")
    assertEquals(entry.secondsPlayed, 50)
  }

  @Test
  fun testTimeTrackingInfoFromBytesNoEntries() {
    val timeTrackingInfoBytes = "{" +
      "\"accountId\":\"accountId\"," +
      "\"bookId\":\"bookId\"," +
      "\"libraryId\":\"libraryId\"," +
      "\"uri\":\"https://projectpalace.io/timeTracking\"," +
      "\"timeEntries\": []" +
      "}"

    val timeTrackingInfo = TimeTrackingJSON.convertBytesToTimeTrackingInfo(
      bytes = timeTrackingInfoBytes.toByteArray()
    )

    assertNotNull(timeTrackingInfo)
    assertEquals(timeTrackingInfo?.accountId, "accountId")
    assertEquals(timeTrackingInfo?.bookId, "bookId")
    assertEquals(timeTrackingInfo?.libraryId, "libraryId")
    assertEquals(timeTrackingInfo?.timeTrackingUri, URI.create("https://projectpalace.io/timeTracking"))
    assertEquals(timeTrackingInfo?.timeEntries, emptyList<TimeTrackingEntry>())
  }

  @Test
  fun testInvalidTimeTrackingInfoFromBytes() {
    val timeTrackingInfoBytes = "{" +
      "\"bookId\":\"bookId\"" +
      "}"

    val timeTrackingInfo = TimeTrackingJSON.convertBytesToTimeTrackingInfo(
      bytes = timeTrackingInfoBytes.toByteArray()
    )

    assertNull(timeTrackingInfo)
  }

  @Test
  fun testConvertTimeTrackingInfoToLocalJSON() {
    val timeTrackingInfo = TimeTrackingInfo(
      accountId = "accountId",
      bookId = "bookId",
      libraryId = "libraryId",
      timeTrackingUri = URI.create("https://palaceproject.io/timeTracking"),
      timeEntries = listOf(
        TimeTrackingEntry(
          id = "id",
          duringMinute = "2023-08-08T10:50Z",
          secondsPlayed = 40
        )
      )
    )

    val objectNode = TimeTrackingJSON.convertTimeTrackingToLocalJSON(
      objectMapper = objectMapper,
      timeTrackingInfo = timeTrackingInfo
    )

    assertNotNull(objectNode)
    assertEquals(objectNode.get("accountId").asText(), timeTrackingInfo.accountId)
    assertEquals(objectNode.get("bookId").asText(), timeTrackingInfo.bookId)
    assertEquals(objectNode.get("libraryId").asText(), timeTrackingInfo.libraryId)
    assertEquals(objectNode.get("uri").asText(), timeTrackingInfo.timeTrackingUri.toString())

    val entriesArray = JSONParserUtilities.getArray(objectNode, "timeEntries")
    assertEquals(entriesArray.size(), timeTrackingInfo.timeEntries.size)

    val entry = entriesArray.first()
    val timeTrackingEntry = TimeTrackingEntry(
      id = entry.get("id").asText(),
      duringMinute = entry.get("duringMinute").asText(),
      secondsPlayed = entry.get("secondsPlayed").asInt()
    )
    assertEquals(timeTrackingEntry, timeTrackingInfo.timeEntries.first())
  }

  @Test
  fun testConvertTimeTrackingInfoToBytesForServerRequest() {
    val timeTrackingInfo = TimeTrackingInfo(
      accountId = "accountId",
      bookId = "bookId",
      libraryId = "libraryId",
      timeTrackingUri = URI.create("https://palaceproject.io/timeTracking"),
      timeEntries = listOf(
        TimeTrackingEntry(
          id = "id",
          duringMinute = "2023-08-08T10:50Z",
          secondsPlayed = 40
        )
      )
    )

    val bytes = TimeTrackingJSON.convertTimeTrackingInfoToBytes(
      objectMapper = objectMapper,
      timeTrackingInfo = timeTrackingInfo
    )

    val objectNode = objectMapper.readTree(bytes)

    // converting to bytes should "clear" the accountId and URI fields as they are not needed on the server
    assertNotNull(objectNode)
    assertFalse(objectNode.has("accountId"))
    assertFalse(objectNode.has("uri"))
  }

  @Test
  fun testConvertServerResponse() {
    val responseString = "{" +
      "\"responses\":[" +
      "{\"id\":\"id\",\"message\":\"Success!\",\"status\":201}" +
      "]," +
      "\"summary\":{" +
      "\"successes\":1," +
      "\"failures\":0," +
      "\"total\":1" +
      "}" +
      "}"

    val objectNode = objectMapper.readTree(responseString) as ObjectNode
    val response = TimeTrackingJSON.convertServerResponseToTimeTrackingResponse(objectNode)

    assertNotNull(response)
    assertEquals(response?.summary?.successes, 1)
    assertEquals(response?.summary?.failures, 0)
    assertEquals(response?.summary?.total, 1)
    assertEquals(response?.responses.orEmpty().size, 1)
    assertEquals(response?.responses?.first()?.id, "id")
    assertEquals(response?.responses?.first()?.message, "Success!")
    assertEquals(response?.responses?.first()?.status, 201)
  }
}
