package org.nypl.simplified.tests.opds

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.nypl.simplified.opds.core.OPDSJSONParser.Companion.newParser
import java.io.FileNotFoundException
import java.io.InputStream

class OPDSJSONParserTest {
  @Test
  @Throws(Exception::class)
  fun testCompatibility20180921_1() {
    val json_parser =
      newParser()

    val e0 =
      json_parser.parseAcquisitionFeedEntryFromStream(
        getResource("compatibility-20180921-test-old.json")
      )

    val e1 =
      json_parser.parseAcquisitionFeedEntryFromStream(
        getResource("compatibility-20180921-test-new-1.json")
      )

    run {
      Assertions.assertEquals(e0.acquisitions, e1.acquisitions)
      Assertions.assertEquals(e0.availability, e1.availability)
      Assertions.assertEquals(e0.authors, e1.authors)
      Assertions.assertEquals(e0.categories, e1.categories)
      Assertions.assertEquals(e0.cover, e1.cover)
      Assertions.assertEquals(e0.groups, e1.groups)
      Assertions.assertEquals(e0.id, e1.id)
      Assertions.assertEquals(e0.narrators, e1.narrators)
      Assertions.assertEquals(e0.previewAcquisitions, e1.previewAcquisitions)
      Assertions.assertEquals(e0.published, e1.published)
      Assertions.assertEquals(e0.publisher, e1.publisher)
      Assertions.assertEquals(e0.summary, e1.summary)
      Assertions.assertEquals(e0.thumbnail, e1.thumbnail)
      Assertions.assertEquals(e0.title, e1.title)
    }
  }

  @Test
  @Throws(Exception::class)
  fun testCompatibility20180921_2() {
    val json_parser =
      newParser()

    val e0 =
      json_parser.parseAcquisitionFeedEntryFromStream(
        getResource("compatibility-20180921-test-old.json")
      )

    val e1 =
      json_parser.parseAcquisitionFeedEntryFromStream(
        getResource("compatibility-20180921-test-new-0.json")
      )

    run {
      val e0a = e0.acquisitions.get(0)
      val e1a = e1.acquisitions.get(0)

      Assertions.assertEquals(
        e0a.availableFinalContentTypes(),
        e1a.availableFinalContentTypes()
      )
      Assertions.assertEquals(e0.availability, e1.availability)
      Assertions.assertEquals(e0.authors, e1.authors)
      Assertions.assertEquals(e0.categories, e1.categories)
      Assertions.assertEquals(e0.cover, e1.cover)
      Assertions.assertEquals(e0.groups, e1.groups)
      Assertions.assertEquals(e0.id, e1.id)
      Assertions.assertEquals(e0.previewAcquisitions, e1.previewAcquisitions)
      Assertions.assertEquals(e0.published, e1.published)
      Assertions.assertEquals(e0.publisher, e1.publisher)
      Assertions.assertEquals(e0.summary, e1.summary)
      Assertions.assertEquals(e0.thumbnail, e1.thumbnail)
      Assertions.assertEquals(e0.title, e1.title)
    }
  }

  companion object {
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
