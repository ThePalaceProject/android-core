package org.nypl.simplified.tests.opds

import one.irradia.mime.api.MIMEType
import one.irradia.mime.vanilla.MIMEParser.Companion.parseRaisingException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.nypl.simplified.opds.core.DRMLicensor
import org.nypl.simplified.opds.core.OPDSAcquisition
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser.Companion.newParser
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParserType
import org.nypl.simplified.opds.core.OPDSAvailabilityHeld
import org.nypl.simplified.opds.core.OPDSAvailabilityHeldReady
import org.nypl.simplified.opds.core.OPDSAvailabilityHoldable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoanable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoaned
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess
import org.nypl.simplified.opds.core.OPDSDateParsers.dateTimeParser
import org.nypl.simplified.opds.core.OPDSIndirectAcquisition.Companion.findTypeInOptional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URI

/**
 * Entry parser contract.
 */
class OPDSFeedEntryParserTest {
  @Test
  @Throws(Exception::class)
  fun testEntryAvailabilityLoanable() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource(
        "entry-availability-loanable.xml"
      )!!
    )

    val availability = e.availability
    val expected = OPDSAvailabilityLoanable.get()
    Assertions.assertEquals(expected, availability)

    Assertions.assertEquals(1, e.acquisitions.size)
  }

  @Test
  @Throws(Exception::class)
  fun testEntryAvailabilityLoanedIndefinite() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource(
        "entry-availability-loaned-indefinite.xml"
      )!!
    )

    val availability = 
      e.availability
    val expectedStartDate =
      dateTimeParser().parseDateTime("2000-01-01T00:00:00Z")
    val expectedEndDate = null
    val expectedRevoke =
      URI("http://example.com/revoke")
    val expected = 
      OPDSAvailabilityLoaned.get(expectedStartDate, expectedEndDate, expectedRevoke)

    Assertions.assertEquals(expected, availability)

    Assertions.assertEquals(1, e.acquisitions.size)
    val acquisition = e.acquisitions.get(0)
    Assertions.assertTrue(
      findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.indirectAcquisitions
      ).isSome(),
      "application/epub+zip is available"
    )
  }

  @Test
  @Throws(Exception::class)
  fun testEntryAvailabilityLoanedTimed() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource(
        "entry-availability-loaned-timed.xml"
      )!!
    )

    val availability = e.availability

    val expectedStartDate = 
      dateTimeParser().parseDateTime("2000-01-01T00:00:00Z")
    val expectedEndDate = 
      dateTimeParser().parseDateTime("2010-01-01T00:00:00Z")
    val expectedRevoke =
     URI("http://example.com/revoke")
    val expected = 
      OPDSAvailabilityLoaned.get(expectedStartDate, expectedEndDate, expectedRevoke)

    Assertions.assertEquals(expected, availability)

    Assertions.assertEquals(1, e.narrators.size)
    Assertions.assertEquals(1, e.acquisitions.size)
    val acquisition = e.acquisitions.get(0)
    Assertions.assertTrue(
      findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.indirectAcquisitions
      ).isSome(),
      "application/epub+zip is available"
    )
  }

  @Test
  @Throws(Exception::class)
  fun testEntryAvailabilityHoldable() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource(
        "entry-availability-holdable.xml"
      )!!
    )

    val availability = e.availability
    val expected = OPDSAvailabilityHoldable.get()

    Assertions.assertEquals(expected, availability)
    Assertions.assertEquals(1, e.acquisitions.size)

    val acquisition = e.acquisitions.get(0)
    Assertions.assertTrue(
      findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.indirectAcquisitions
      ).isSome(),
      "application/epub+zip is available"
    )
  }

  @Test
  @Throws(Exception::class)
  fun testEntryAvailabilityHeldIndefinite() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource(
        "entry-availability-held-indefinite.xml"
      )!!
    )

    val availability = e.availability

    val expectedStartDate =
      dateTimeParser().parseDateTime("2000-01-01T00:00:00Z")
    val queuePosition =
      null
    val expectedEndDate = 
      null
    val expectedRevoke =
      URI("http://example.com/revoke")
    val expected = 
      OPDSAvailabilityHeld.get(expectedStartDate, queuePosition, expectedEndDate, expectedRevoke)

    Assertions.assertEquals(expected, availability)
    Assertions.assertEquals(1, e.acquisitions.size)

    val acquisition = e.acquisitions.get(0)
    Assertions.assertTrue(
      findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.indirectAcquisitions
      ).isSome(),
      "application/epub+zip is available"
    )
  }

  @Test
  @Throws(Exception::class)
  fun testEntryAvailabilityHeldTimed() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource(
        "entry-availability-held-timed.xml"
      )!!
    )

    val availability = e.availability

    val expectedStartDate =
      dateTimeParser().parseDateTime("2000-01-01T00:00:00Z")
    val expectedEndDate =
      dateTimeParser().parseDateTime("2010-01-01T00:00:00Z")
    val queuePosition =
      null
    val expectedRevoke =
     URI("http://example.com/revoke")
    val expected =
      OPDSAvailabilityHeld.get(expectedStartDate, queuePosition, expectedEndDate, expectedRevoke)

    Assertions.assertEquals(expected, availability)
    Assertions.assertEquals(1, e.acquisitions.size)

    val acquisition = e.acquisitions.get(0)
    Assertions.assertTrue(
      findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.indirectAcquisitions
      ).isSome(),
      "application/epub+zip is available"
    )
  }

  @Test
  @Throws(Exception::class)
  fun testEntryAvailabilityHeldIndefiniteQueued() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource(
        "entry-availability-held-indefinite-queued.xml"
      )!!
    )

    val availability = e.availability

    val expectedStartDate =
      dateTimeParser().parseDateTime("2000-01-01T00:00:00Z")
    val queuePosition = 3
    val expectedEndDate = null
    val expectedRevoke =
      URI("http://example.com/revoke")
    val expected =
      OPDSAvailabilityHeld.get(expectedStartDate, queuePosition, expectedEndDate, expectedRevoke)

    Assertions.assertEquals(expected, availability)
    Assertions.assertEquals(1, e.acquisitions.size)

    val acquisition = e.acquisitions.get(0)
    Assertions.assertTrue(
      findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.indirectAcquisitions
      ).isSome(),
      "application/epub+zip is available"
    )
  }

  @Test
  @Throws(Exception::class)
  fun testEntryAvailabilityHeldTimedQueued() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource(
        "entry-availability-held-timed-queued.xml"
      )!!
    )

    val availability = e.availability

    val expectedStartDate =
      dateTimeParser().parseDateTime("2000-01-01T00:00:00Z")
    val expectedEndDate =
      dateTimeParser().parseDateTime("2010-01-01T00:00:00Z")
    val queuePosition = 3
    val expectedRevoke =
      URI("http://example.com/revoke")
    val expected =
      OPDSAvailabilityHeld.get(expectedStartDate, queuePosition, expectedEndDate, expectedRevoke)

    Assertions.assertEquals(expected, availability)
    Assertions.assertEquals(1, e.acquisitions.size)

    val acquisition = e.acquisitions.get(0)
    Assertions.assertTrue(
      findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.indirectAcquisitions
      ).isSome(),
      "application/epub+zip is available"
    )
  }

  @Test
  @Throws(Exception::class)
  fun testEntryAvailabilityHeldReady() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource(
        "entry-availability-heldready.xml"
      )!!
    )

    val availability = e.availability
    val expectedEndDate =
      null
    val expectedRevoke =
      URI("http://example.com/revoke")
    val expected =
      OPDSAvailabilityHeldReady.get(expectedEndDate, expectedRevoke)

    Assertions.assertEquals(expected, availability)
    Assertions.assertEquals(1, e.acquisitions.size)

    val acquisition = e.acquisitions.get(0)
    Assertions.assertTrue(
      findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.indirectAcquisitions
      ).isSome(),
      "application/epub+zip is available"
    )
  }

  @Test
  @Throws(Exception::class)
  fun testEntryAvailabilityReservedTimed() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource(
        "entry-availability-heldready-timed.xml"
      )!!
    )

    val availability = e.availability

    val expectedEndDate =
      dateTimeParser().parseDateTime("2010-01-01T00:00:00Z")
    val expectedRevoke =
      URI("http://example.com/revoke")
    val expected =
      OPDSAvailabilityHeldReady.get(expectedEndDate, expectedRevoke)

    Assertions.assertEquals(expected, availability)
    Assertions.assertEquals(1, e.acquisitions.size)

    val acquisition = e.acquisitions.get(0)
    Assertions.assertTrue(
      findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.indirectAcquisitions
      ).isSome(),
      "application/epub+zip is available"
    )
  }

  @Test
  @Throws(Exception::class)
  fun testEntryAvailabilityOpenAccess() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource(
        "entry-availability-open-access.xml"
      )!!
    )

    val availability = e.availability

    val expectedRevoke =
      URI("http://example.com/revoke")
    val expected =
      OPDSAvailabilityOpenAccess.get(expectedRevoke)

    Assertions.assertEquals(expected, availability)
    Assertions.assertEquals(1, e.acquisitions.size)

    val acquisition = e.acquisitions.get(0)
    Assertions.assertEquals(0, acquisition.indirectAcquisitions.size)
    Assertions.assertEquals(mimeOf("application/epub+zip"), acquisition.type)
  }

  @Test
  @Throws(Exception::class)
  fun testEntryAvailabilityReservedSpecific0() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource(
        "entry-availability-heldready-specific0.xml"
      )!!
    )

    val availability = e.availability

    val expectedEndDate =
      dateTimeParser().parseDateTime("2015-08-24T00:30:24Z")
    val expectedRevoke =
      URI("http://example.com/revoke")
    val expected =
      OPDSAvailabilityHeldReady.get(expectedEndDate, expectedRevoke)

    Assertions.assertEquals(expected, availability)
    Assertions.assertEquals(1, e.acquisitions.size)

    val acquisition = e.acquisitions.get(0)
    Assertions.assertTrue(
      findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.indirectAcquisitions
      ).isSome(),
      "application/epub+zip is available"
    )
  }

  @Test
  @Throws(Exception::class)
  fun testEntryMultipleFormats0() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource("entry-with-formats-0.xml")!!
    )

    Assertions.assertEquals(3, e.acquisitions.size)

    run {
      val acquisition = e.acquisitions.get(0)
      Assertions.assertTrue(
        findTypeInOptional(
          mimeOf("application/epub+zip"),
          acquisition.indirectAcquisitions
        ).isSome(),
        "application/epub+zip is available"
      )
    }

    run {
      val acquisition = e.acquisitions.get(1)
      Assertions.assertTrue(
        findTypeInOptional(
          mimeOf("application/pdf"),
          acquisition.indirectAcquisitions
        ).isSome(),
        "application/pdf is available"
      )
    }

    run {
      val acquisition = e.acquisitions.get(2)
      Assertions.assertTrue(
        findTypeInOptional(
          mimeOf("text/html"),
          acquisition.indirectAcquisitions
        ).isSome(),
        "text/html is available"
      )
    }
  }

  @Test
  @Throws(Exception::class)
  fun testEntryMultipleFormats1() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource("entry-with-formats-1.xml")!!
    )

    Assertions.assertEquals(1, e.acquisitions.size)

    val acquisition = e.acquisitions.get(0)
    Assertions.assertTrue(
      findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.indirectAcquisitions
      ).isSome(),
      "application/epub+zip is available"
    )
  }

  @Test
  @Throws(Exception::class)
  fun testEntryWithDRM() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource("entry-with-drm.xml")!!
    )

    val licensor: DRMLicensor = e.licensor!!
    Assertions.assertEquals(
      "NYPL",
      licensor.vendor
    )
    Assertions.assertEquals(
      "NYNYPL|0000000000|XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX|XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
      licensor.clientToken
    )
    Assertions.assertEquals(
      "http://qa.circulation.librarysimplified.org/NYNYPL/AdobeAuth/devices",
      licensor.deviceManager
    )
  }

  @Test
  @Throws(Exception::class)
  fun testEntryDateBug() {
    val parser = this.parser
    parser.parseEntryStream(
      URI.create("urn:test"),
      getResource("date-bug.xml")!!
    )
  }

  @Test
  @Throws(Exception::class)
  fun testBugPP465() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource(
        "bug-pp-465.xml"
      )!!
    )

    Assertions.assertEquals(1, e.acquisitions.size)
    Assertions.assertEquals(mutableListOf<Any?>(), e.authors)
  }

  private val parser: OPDSAcquisitionFeedEntryParserType
    get() = newParser()

  private fun mimeOf(text: String): MIMEType {
    try {
      return parseRaisingException(text)
    } catch (e: Exception) {
      throw IllegalStateException(e)
    }
  }

  @Test
  @Throws(Exception::class)
  fun testEntryBoundless1() {
    val parser = this.parser
    val e = parser.parseEntryStream(
      URI.create("urn:test"),
      getResource("boundless-1.xml")!!
    )

    val a: List<OPDSAcquisition> = e.acquisitions
    Assertions.assertEquals(1, a.size)
    Assertions.assertEquals(
      "https://minotaur.dev.palaceproject.io/minotaur-test-library/works/796/fulfill/30{?modulus,exponent,device_id}",
      a.get(0).uri.toTemplated().href
    )
  }

  companion object {
    private val LOG: Logger? = LoggerFactory.getLogger(OPDSFeedEntryParserTest::class.java)

    @Throws(Exception::class)
    private fun getResource(
      name: String
    ): InputStream? {
      val path = "/org/nypl/simplified/tests/opds/" + name
      val url = OPDSFeedEntryParserTest::class.java.getResource(path)
      if (url == null) {
        throw FileNotFoundException(path)
      }
      return url.openStream()
    }
  }
}
