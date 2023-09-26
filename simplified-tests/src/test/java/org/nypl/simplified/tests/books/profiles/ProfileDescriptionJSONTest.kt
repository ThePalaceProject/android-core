package org.nypl.simplified.tests.books.profiles

import com.fasterxml.jackson.databind.ObjectMapper
import org.joda.time.DateTime
import org.joda.time.DateTimeUtils
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.nypl.simplified.accounts.api.AccountID

import org.nypl.simplified.profiles.ProfileDescriptionJSON
import org.nypl.simplified.profiles.api.ProfileAttributes
import org.nypl.simplified.profiles.api.ProfileDateOfBirth
import org.nypl.simplified.profiles.api.ProfileDescription
import org.nypl.simplified.profiles.api.ProfilePreferences
import org.nypl.simplified.reader.api.ReaderColorScheme
import org.nypl.simplified.reader.api.ReaderFontSelection
import org.nypl.simplified.reader.api.ReaderPreferences
import org.slf4j.LoggerFactory

class ProfileDescriptionJSONTest {

  private val logger = LoggerFactory.getLogger(ProfileDescriptionJSONTest::class.java)
  private val currentDateTimeZoneSystem = DateTimeZone.getDefault()

  @BeforeEach
  fun setUp() {
    DateTimeUtils.setCurrentMillisFixed(0L)
    DateTimeZone.setDefault(DateTimeZone.UTC)
  }

  @AfterEach
  fun tearDown() {
    DateTimeUtils.setCurrentMillisSystem()
    DateTimeZone.setDefault(currentDateTimeZoneSystem)
  }

  @Test
  fun testRoundTrip() {
    val mapper = ObjectMapper()

    val dateTime =
      DateTime.parse("2010-01-01T00:00:00Z")

    val description_0 =
      ProfileDescription(
        displayName = "Kermit",
        preferences = ProfilePreferences(
          ProfileDateOfBirth(dateTime, true),
          showTestingLibraries = false,
          hasSeenLibrarySelectionScreen = false,
          readerPreferences = ReaderPreferences.builder().build(),
          mostRecentAccount = AccountID.generate(),
          playbackRates = hashMapOf(),
          sleepTimers = hashMapOf(),
          areNotificationsEnabled = false,
          isManualLCPPassphraseEnabled = false
        ),
        attributes = ProfileAttributes(
          sortedMapOf(
            Pair("a", "b"),
            Pair("c", "d"),
            Pair("e", "f")
          )
        )
      )

    val node =
      ProfileDescriptionJSON.serializeToJSON(mapper, description_0)
    val description_1 =
      ProfileDescriptionJSON.deserializeFromJSON(mapper, node, AccountID.generate())

    this.logger.debug("{}", ProfileDescriptionJSON.serializeToString(ObjectMapper(), description_1))
    assertEquals(description_0, description_1)
  }

  @Test
  fun testLFA_0() {
    val mapper = ObjectMapper()
    val mostRecentAccount = AccountID.generate()
    val description =
      ProfileDescriptionJSON.deserializeFromText(
        mapper,
        this.ofResource("profile-lfa-0.json"),
        mostRecentAccount
      )

    this.logger.debug("{}", ProfileDescriptionJSON.serializeToString(ObjectMapper(), description))
    assertEquals("Eggbert", description.displayName)
    assertEquals("developer", description.attributes.role)
    assertEquals(mostRecentAccount, description.preferences.mostRecentAccount)
  }

  @Test
  fun testLFA_1() {
    val mapper = ObjectMapper()
    val mostRecentAccount = AccountID.generate()
    val description =
      ProfileDescriptionJSON.deserializeFromText(
        mapper,
        this.ofResource("profile-lfa-1.json"),
        mostRecentAccount
      )

    this.logger.debug("{}", ProfileDescriptionJSON.serializeToString(ObjectMapper(), description))
    assertEquals("Newbert", description.displayName)
    assertEquals("male", description.attributes.gender)
    assertEquals("student", description.attributes.role)
    assertEquals("ຊັ້ນ 8", description.attributes.grade)
    assertEquals("ສົ້ນຂົວ", description.attributes.school)
    assertEquals(mostRecentAccount, description.preferences.mostRecentAccount)
  }

  @Test
  fun testNYPL_0() {
    val mapper = ObjectMapper()
    val mostRecentAccount = AccountID.generate()
    val description =
      ProfileDescriptionJSON.deserializeFromText(
        mapper,
        this.ofResource("profile-nypl-0.json"),
        mostRecentAccount
      )

    this.logger.debug("{}", ProfileDescriptionJSON.serializeToString(ObjectMapper(), description))
    assertEquals("", description.displayName)
    assertEquals(mostRecentAccount, description.preferences.mostRecentAccount)
  }

  @Test
  fun testSMA92() {
    val mapper = ObjectMapper()
    val mostRecentAccountFallback = AccountID.generate()
    val description =
      ProfileDescriptionJSON.deserializeFromText(
        mapper,
        this.ofResource("profile-sma-92.json"),
        mostRecentAccountFallback
      )

    assertEquals("", description.displayName)
    assertEquals(0, description.preferences.playbackRates.size)
    assertEquals(0, description.preferences.sleepTimers.size)
    assertEquals(1.0, description.preferences.readerPreferences.brightness())
    assertEquals(100.0, description.preferences.readerPreferences.fontScale())
    assertEquals(ReaderFontSelection.READER_FONT_OPEN_DYSLEXIC, description.preferences.readerPreferences.fontFamily())
    assertEquals(ReaderColorScheme.SCHEME_WHITE_ON_BLACK, description.preferences.readerPreferences.colorScheme())
    assertEquals("5310437f-db1a-492e-a09f-1ceaa43303dd", description.preferences.mostRecentAccount.uuid.toString())
  }

  @Test
  fun testPlaybackRates() {
    val mapper = ObjectMapper()
    val mostRecentAccountFallback = AccountID.generate()
    val description =
      ProfileDescriptionJSON.deserializeFromText(
        mapper,
        this.ofResource("profile-rates.json"),
        mostRecentAccountFallback
      )

    assertEquals(2, description.preferences.playbackRates.size)
    assertEquals("bookid1", description.preferences.playbackRates.keys.first())
    assertEquals("bookid2", description.preferences.playbackRates.keys.last())
    assertEquals(1.0, description.preferences.playbackRates["bookid1"]?.speed)
    assertEquals(1.25, description.preferences.playbackRates["bookid2"]?.speed)
  }

  @Test
  fun testSleepTimers() {
    val mapper = ObjectMapper()
    val mostRecentAccountFallback = AccountID.generate()
    val description =
      ProfileDescriptionJSON.deserializeFromText(
        mapper,
        this.ofResource("profile-sleep-timers.json"),
        mostRecentAccountFallback
      )

    assertEquals(2, description.preferences.sleepTimers.size)
    assertEquals("bookid1", description.preferences.sleepTimers.keys.first())
    assertEquals("bookid2", description.preferences.sleepTimers.keys.last())
    assertEquals(Duration.standardMinutes(15L).millis, description.preferences.sleepTimers["bookid1"])
    assertEquals(Duration.standardMinutes(30L).millis, description.preferences.sleepTimers["bookid2"])
  }

  /**
   * A large font scale value should not be constrained during deserialization.
   */

  @Test
  fun testLargeFontScale() {
    val mapper = ObjectMapper()
    val mostRecentAccount = AccountID.generate()
    val description =
      ProfileDescriptionJSON.deserializeFromText(
        mapper,
        this.ofResource("profile-large-font-scale.json"),
        mostRecentAccount
      )

    assertEquals("", description.displayName)
    assertEquals(1.0, description.preferences.readerPreferences.brightness())
    assertEquals(800.0, description.preferences.readerPreferences.fontScale())
    assertEquals(ReaderFontSelection.READER_FONT_SANS_SERIF, description.preferences.readerPreferences.fontFamily())
    assertEquals(ReaderColorScheme.SCHEME_BLACK_ON_WHITE, description.preferences.readerPreferences.colorScheme())
    assertEquals(mostRecentAccount, description.preferences.mostRecentAccount)
  }

  private fun ofResource(name: String): String {
    val bytes =
      ProfileDescriptionJSONTest::class.java.getResourceAsStream(
        "/org/nypl/simplified/tests/books/$name"
      )!!.readBytes()

    val text = String(bytes)
    this.logger.debug("{}: {}", name, text)
    return text
  }
}
