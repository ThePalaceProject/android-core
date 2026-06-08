package org.nypl.simplified.tests.opds

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSFeedParser.Companion.newParser
import org.nypl.simplified.opds.core.OPDSJSONParser
import org.nypl.simplified.opds.core.OPDSJSONSerializer.Companion.newSerializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URI

class OPDSJSONSerializerTest {
  @Test
  @Throws(Exception::class)
  fun testRoundTrip0() {
    val p =
      OPDSAcquisitionFeedEntryParser.newParser()

    val jp = OPDSJSONParser.newParser()

    val s = newSerializer()

    val rs0: InputStream =
      getResource("entry-0.xml")
    val e0 = p.parseEntryStream(URI.create("urn:test"), rs0)

    val bao0 = ByteArrayOutputStream()
    s.serializeToStream(s.serializeFeedEntry(e0), bao0)

    val rs1: InputStream = ByteArrayInputStream(bao0.toByteArray())
    val e1 =
      jp.parseAcquisitionFeedEntryFromStream(rs1)

    run {
      val e0a = e0.acquisitions
      val e1a = e1.acquisitions
      Assertions.assertEquals(e0a.size, e1a.size)

      for (index in e0a.indices) {
        val a0 = e0a.get(index)
        val a1 = e1a.get(index)
        Assertions.assertEquals(a0.relation, a1.relation)
        Assertions.assertEquals(a0.type, a1.type)
        val u0 = a0.uri
        val u1 = a1.uri
        Assertions.assertEquals(u0.bitrate, u1.bitrate)
        Assertions.assertEquals(u0.duration, u1.duration)
        Assertions.assertEquals(u0.height, u1.height)
        Assertions.assertEquals(u0.hrefURI, u1.hrefURI)
        Assertions.assertEquals(u0.relation, u1.relation)
        Assertions.assertEquals(u0.title, u1.title)
        Assertions.assertEquals(u0.type.toString(), u1.type.toString())
        Assertions.assertEquals(u0.width, u1.width)
        Assertions.assertEquals(a0.indirectAcquisitions, a1.indirectAcquisitions)
      }

      Assertions.assertEquals(e0.availability, e1.availability)
      Assertions.assertEquals(e0.authors, e1.authors)
      Assertions.assertEquals(e0.categories, e1.categories)
      Assertions.assertEquals(e0.cover, e1.cover)
      Assertions.assertEquals(e0.groups, e1.groups)
      Assertions.assertEquals(e0.id, e1.id)
      Assertions.assertEquals(e0.narrators, e1.narrators)
      Assertions.assertEquals(e0.previewAcquisitions, e1.previewAcquisitions)
      // Assertions.assertEquals(e0.published, e1.published)
      Assertions.assertEquals(e0.publisher, e1.publisher)
      Assertions.assertEquals(e0.summary, e1.summary)
      Assertions.assertEquals(e0.thumbnail, e1.thumbnail)
      Assertions.assertEquals(e0.title, e1.title)
      Assertions.assertEquals(e0.updated, e1.updated)
    }
  }

  @Test
  @Throws(Exception::class)
  fun testRoundTrip1() {
    val ep =
      OPDSAcquisitionFeedEntryParser.newParser()

    val p = newParser(ep)
    val jp = OPDSJSONParser.newParser()

    val s = newSerializer()

    val rs0: InputStream = getResource("loans.xml")
    val fe0 = p.parse(URI("http://example.com"), rs0)

    val bao0 = ByteArrayOutputStream()
    s.serializeToStream(s.serializeFeed(fe0), bao0)

    val rs1: InputStream = ByteArrayInputStream(bao0.toByteArray())
    val fe1 = jp.parseAcquisitionFeedFromStream(rs1)

    run {
      val fe0e = fe0.feedEntries
      val fe1e = fe1.feedEntries
      for (index in fe0e.indices) {
        val e0 = fe0e.get(index)
        val e1 = fe1e.get(index)
        Assertions.assertEquals(e0.acquisitions, e1.acquisitions)
        Assertions.assertEquals(e0.authors, e1.authors)
        Assertions.assertEquals(e0.categories, e1.categories)
        Assertions.assertEquals(e0.cover, e1.cover)
        Assertions.assertEquals(e0.groups, e1.groups)
        Assertions.assertEquals(e0.id, e1.id)
        Assertions.assertEquals(e0.narrators, e1.narrators)
        Assertions.assertEquals(e0.previewAcquisitions, e1.previewAcquisitions)
        Assertions.assertEquals(e0.publisher, e1.publisher)
        Assertions.assertEquals(e0.summary, e1.summary)
        Assertions.assertEquals(e0.thumbnail, e1.thumbnail)
        Assertions.assertEquals(e0.title, e1.title)
        // Forget comparing instances of Calendar, no implementation gets this
        // right
        // Assert.assertEquals(e0.getUpdated(), e1.getUpdated());
        // Assert.assertEquals(e0.getPublished(), e1.getPublished());
      }
    }
  }

  @Test
  @Throws(Exception::class)
  fun testMimeTypes() {
    val p =
      OPDSAcquisitionFeedEntryParser.newParser()

    val jp = OPDSJSONParser.newParser()

    val s = newSerializer()

    val rs0: InputStream =
      getResource("entry-0.xml")
    val e0 = p.parseEntryStream(URI.create("urn:test"), rs0)

    val bao0 = ByteArrayOutputStream()
    s.serializeToStream(s.serializeFeedEntry(e0), bao0)

    val rs1: InputStream = ByteArrayInputStream(bao0.toByteArray())
    val e1 =
      jp.parseAcquisitionFeedEntryFromStream(rs1)

    val e0a = e0.acquisitions
    val e1a = e1.acquisitions
    Assertions.assertEquals(e0a.size, 1)
    Assertions.assertEquals(e0a.size, e1a.size)

    val e0FirstAcquisition = e0a.get(0)
    val e1FirstAcquisition = e1a.get(0)

    Assertions.assertEquals(
      e0FirstAcquisition.type.subtype,
      e1FirstAcquisition.type.subtype
    )
    Assertions.assertEquals(
      e0FirstAcquisition.type.type,
      e1FirstAcquisition.type.type
    )
    Assertions.assertEquals(
      e0FirstAcquisition.type.fullType,
      e1FirstAcquisition.type.fullType
    )
    Assertions.assertEquals(
      e0FirstAcquisition.type.parameters.size,
      e1FirstAcquisition.type.parameters.size
    )

    for (key in e0FirstAcquisition.type.parameters.keys) {
      Assertions.assertTrue(e1FirstAcquisition.type.parameters.containsKey(key))
      Assertions.assertEquals(
        e0FirstAcquisition.type.parameters.get(key),
        e1FirstAcquisition.type.parameters.get(key)
      )
    }
  }

  companion object {
    @Throws(Exception::class)
    private fun getResource(
      name: String
    ): InputStream {
      val path = "/org/nypl/simplified/tests/opds/" + name
      val url = OPDSFeedEntryParserTest::class.java.getResource(path)
      if (url == null) {
        throw FileNotFoundException(path)
      }
      return url.openStream()
    }
  }
}
